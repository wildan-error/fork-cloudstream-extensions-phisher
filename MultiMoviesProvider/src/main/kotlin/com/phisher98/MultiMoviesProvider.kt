package com.phisher98


import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import org.jsoup.nodes.Element
import com.lagradost.nicehttp.NiceResponse
import okhttp3.FormBody
import java.net.URI

class MultiMoviesProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://multimovies.press"
    override var name = "MultiMovies"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AnimeMovie,
    )

    companion object {
        //val headers= mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:101.0) Gecko/20100101 Firefox/101.0", "X-Requested-With" to "XMLHttpRequest")
    }

    override val mainPage = mainPageOf(
        "$mainUrl/trending/" to "Trending",
        "$mainUrl/genre/bollywood-movies/" to "Bollywood Movies",
        "$mainUrl/genre/hollywood/" to "Hollywood Movies",
        "$mainUrl/genre/south-indian/" to "South Indian Movies",
        "$mainUrl/genre/punjabi/" to "Punjabi Movies",
        "$mainUrl/genre/amazon-prime/" to "Amazon Prime",
        "$mainUrl/genre/disney-hotstar/" to "Disney Hotstar",
        "$mainUrl/genre/jio-ott/" to "Jio OTT",
        "$mainUrl/genre/netflix/" to "Netfilx",
        "$mainUrl/genre/sony-liv/" to "Sony Live",
        "$mainUrl/genre/k-drama/" to "KDrama",
        "$mainUrl/genre/zee-5/" to "Zee5",
        "$mainUrl/genre/anime-hindi/" to "Anime Series",
        "$mainUrl/genre/anime-movies/" to "Anime Movies",
        "$mainUrl/genre/cartoon-network/" to "Cartoon Network",
        "$mainUrl/genre/disney-channel/" to "Disney Channel",
        "$mainUrl/genre/hungama/" to "Hungama",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = if (page == 1) {
            app.get(request.data).document
        } else {
            app.get(request.data + "page/$page/").document
        }
        val home = if (request.data.contains("/movies")) {
            document.select("#archive-content > article").mapNotNull {
                it.toSearchResult()
            }
        } else {
            document.select("div.items > article").mapNotNull {
                it.toSearchResult()
            }
        }
        return newHomePageResponse(HomePageList(request.name, home))
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("div.data > h3 > a")?.text()?.trim() ?: return null
        val href = fixUrl(this.selectFirst("div.data > h3 > a")?.attr("href").toString())
        val posterUrl = fixUrlNull(this.selectFirst("div.poster > img")?.attr("src"))
        val quality = getQualityFromString(this.select("div.poster > div.mepo > span").text())
        return if (href.contains("Movie")) {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                this.quality = quality
            }
        } else {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                this.quality = quality
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        return document.select("div.result-item").mapNotNull {
            val title =
                it.selectFirst("article > div.details > div.title > a")?.text().toString().trim()
            val href = fixUrl(
                it.selectFirst("article > div.details > div.title > a")?.attr("href").toString()
            )
            val posterUrl = fixUrlNull(
                it.selectFirst("article > div.image > div.thumbnail > a > img")?.attr("src")
            )
            val quality = getQualityFromString(it.select("div.poster > div.mepo > span").text())
            val type = it.select("article > div.image > div.thumbnail > a > span").text()
            if (type.contains("Movie")) {
                newMovieSearchResponse(title, href, TvType.Movie) {
                    this.posterUrl = posterUrl
                    this.quality = quality
                }
            } else {
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                    this.posterUrl = posterUrl
                    this.quality = quality
                }
            }
        }
    }

    private suspend fun getEmbed(postid: String?, nume: String, referUrl: String?): NiceResponse {
        val body = FormBody.Builder()
            .addEncoded("action", "doo_player_ajax")
            .addEncoded("post", postid.toString())
            .addEncoded("nume", nume)
            .addEncoded("type", "movie")
            .build()

        return app.post(
            "$mainUrl/wp-admin/admin-ajax.php",
            requestBody = body,
            referer = referUrl
        )
    }

    data class TrailerUrl(
        @JsonProperty("embed_url") var embedUrl: String?,
        @JsonProperty("type") var type: String?
    )

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        val titleL = doc.selectFirst("div.sheader > div.data > h1")?.text()?.trim() ?: return null
        val titleRegex = Regex("(^.*\\)\\d*)")
        val titleClean = titleRegex.find(titleL)?.groups?.get(1)?.value.toString()
        val title = if (titleClean == "null") titleL else titleClean
        val poster = fixUrlNull(
            doc.select("#contenedor").toString().substringAfter("background-image:url(")
                .substringBefore(");")
        )
        val tags = doc.select("div.sgeneros > a").map { it.text() }
        val year = doc.selectFirst("span.date")?.text()?.substringAfter(",")?.trim()?.toInt()
        val description = doc.selectFirst("#info div.wp-content p")?.text()?.trim()
        val type = if (url.contains("tvshows")) TvType.TvSeries else TvType.Movie
        val trailerRegex = Regex("\"http.*\"")

        var trailer: String? = if (type == TvType.Movie) {
            try {
                val postId = doc.select("#player-option-trailer").attr("data-post")
                val embedResponse = getEmbed(postId, "trailer", url)
                val parsed = embedResponse.parsed<TrailerUrl>()
                parsed.embedUrl?.let { fixUrlNull(it) }
            } catch (e: Exception) {
                null
            }
        } else {
            val iframeSrc = doc.select("iframe.rptss").attr("src")
            fixUrlNull(iframeSrc)
        }
        trailer = trailer?.let { trailerRegex.find(it)?.value?.trim('"') }
        val rating = doc.select("span.dt_rating_vgs").text().toRatingInt()
        val duration =
            doc.selectFirst("span.runtime")?.text()?.removeSuffix(" Min.")?.trim()
                ?.toInt()
        val actors =
            doc.select("div.person").map {
                ActorData(
                    Actor(
                        it.select("div.data > div.name > a").text(),
                        it.select("div.img > a > img").attr("src")
                    ),
                    roleString = it.select("div.data > div.caracter").text(),
                )
            }
        val recommendations = doc.select("#dtw_content_related-2 article").mapNotNull {
            it.toSearchResult()
        }

        val episodes = ArrayList<Episode>()
        doc.select("#seasons ul.episodios").mapIndexed { seasonNum, me ->
            me.select("li").mapIndexed { epNum, it ->
                episodes.add(
                    newEpisode(it.select("div.episodiotitle > a").attr("href"))
                    {
                        this.name = it.select("div.episodiotitle > a").text()
                        this.season = seasonNum + 1
                        this.episode = epNum + 1
                        this.posterUrl = it.select("div.imagen > img").attr("src")
                    }
                )
            }
        }

        return if (type == TvType.Movie) {
            newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                url
            ) {
                this.posterUrl = poster?.trim()
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                this.duration = duration
                this.actors = actors
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        } else {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster?.trim()
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                this.duration = duration
                this.actors = actors
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("Phisher",data)
        val req = app.get(data).document
        req.select("ul#playeroptionsul li").map {
                Triple(
                    it.attr("data-post"),
                    it.attr("data-nume"),
                    it.attr("data-type")
                )
            }.amap { (id, nume, type) ->
            if (!nume.contains("trailer")) {
                val source = app.post(
                    url = "$mainUrl/wp-admin/admin-ajax.php",
                    data = mapOf(
                        "action" to "doo_player_ajax",
                        "post" to id,
                        "nume" to nume,
                        "type" to type
                    ),
                    referer = mainUrl,
                    headers = mapOf("X-Requested-With" to "XMLHttpRequest")
                ).parsed<ResponseHash>().embed_url
                val link = source.substringAfter("\"").substringBefore("\"").trim()
                when {
                    !link.contains("youtube") -> {
                        if (link.contains("gdmirrorbot.nl")) {

                            val host = getBaseUrl(app.get(link).url)
                            val embed = link.substringAfterLast("/")
                            val data = mapOf("sid" to embed)
                            val jsonString =
                                app.post("$host/embedhelper.php", data = data).toString()
                            Log.d("Phisher", "$host/embedhelper.php")

                            val jsonElement: JsonElement = JsonParser.parseString(jsonString)
                            if (!jsonElement.isJsonObject) {
                                Log.e(
                                    "Error:",
                                    "Unexpected JSON format: Response is not a JSON object"
                                )
                                return@amap
                            }
                            val jsonObject = jsonElement.asJsonObject
                            val siteUrls =
                                jsonObject["siteUrls"]?.takeIf { it.isJsonObject }?.asJsonObject
                            val mresultEncoded =
                                jsonObject["mresult"]?.takeIf { it.isJsonPrimitive }?.asString
                            val mresult = mresultEncoded?.let {
                                val decodedString = base64Decode(it) // Decode from Base64
                                JsonParser.parseString(decodedString).asJsonObject // Convert to JSON object
                            }
                            val siteFriendlyNames =
                                jsonObject["siteFriendlyNames"]?.takeIf { it.isJsonObject }?.asJsonObject
                            if (siteUrls == null || siteFriendlyNames == null || mresult == null) {
                                return@amap
                            }
                            val commonKeys = siteUrls.keySet().intersect(mresult.keySet())
                            commonKeys.forEach { key ->
                                val siteName = siteFriendlyNames[key]?.asString
                                if (siteName == null) {
                                    Log.e("Error:", "Skipping key: $key because siteName is null")
                                    return@forEach
                                }
                                val siteUrl = siteUrls[key]?.asString
                                val resultUrl = mresult[key]?.asString
                                if (siteUrl == null || resultUrl == null) {
                                    Log.e(
                                        "Error:",
                                        "Skipping key: $key because siteUrl or resultUrl is null"
                                    )
                                    return@forEach
                                }
                                val href = siteUrl + resultUrl
                                Log.d("Phisher", href)

                                loadExtractor(href, subtitleCallback, callback)
                            }
                        } else if (link.contains("deaddrive.xyz")) {
                            app.get(link).document.select("ul.list-server-items > li").map {
                                val server = it.attr("data-video")
                                loadExtractor(server, referer = mainUrl, subtitleCallback, callback)
                            }
                        } else
                            loadExtractor(link, referer = mainUrl, subtitleCallback, callback)
                    }

                    else -> return@amap
                }
            }
        }
        return true
    }

    data class ResponseHash(
        @JsonProperty("embed_url") val embed_url: String,
        @JsonProperty("key") val key: String? = null,
        @JsonProperty("type") val type: String? = null,
    )


    private fun getBaseUrl(url: String): String {
        return URI(url).let {
            "${it.scheme}://${it.host}"
        }
    }
}
