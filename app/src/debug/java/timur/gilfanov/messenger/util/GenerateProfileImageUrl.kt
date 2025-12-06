package timur.gilfanov.messenger.util

fun generateProfileImageUrl(name: String): String = "https://ui-avatars.com/api/?" +
    "name=${name.replace(" ", "+")}&" +
    "background=0D8ABC&" +
    "color=fff"
