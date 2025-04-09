// use an integer for version numbers
version = 3.5


cloudstream {
    language = "id"
    // All of these properties are optional, you can safely remove them

    // description = "Lorem Ipsum"
    authors = listOf("Hexated,Phisher98,REDACTED")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 3 // will be 3 if unspecified
    tvTypes = listOf(
        "AsianDrama",
        "TvSeries",
        "Movie",
    )


    iconUrl = "https://www.google.com/s2/favicons?domain=tv7.lk21.am.in&sz=%size%"

    isCrossPlatform = true
}
