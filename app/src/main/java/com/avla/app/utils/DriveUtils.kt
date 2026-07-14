package com.avla.app.utils

fun convertDriveLinkToDirectUrl(link: String): String {
    if (link.isBlank()) return link
    if (link.contains("thumbnail?id=")) return link

    val fileId = extractDriveFileId(link) ?: return link
    return "https://drive.google.com/thumbnail?id=$fileId&sz=w800"
}

private fun extractDriveFileId(link: String): String? {
    // /file/d/FILE_ID/view
    val slashMatch = Regex("/file/d/([a-zA-Z0-9_-]+)").find(link)
    if (slashMatch != null) return slashMatch.groupValues[1]

    // ?id=FILE_ID or &id=FILE_ID
    val idMatch = Regex("[?&]id=([a-zA-Z0-9_-]+)").find(link)
    if (idMatch != null) return idMatch.groupValues[1]

    return null
}