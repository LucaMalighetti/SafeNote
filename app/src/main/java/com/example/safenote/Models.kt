package com.example.safenote

import android.net.Uri
import java.util.UUID

data class SchoolClass(val name: String, val tags: List<String>)

data class SharedPhoto(
    val id: String = UUID.randomUUID().toString(),
    val uriString: String? = null,
    val uriStrings: List<String>? = emptyList(),
    val ownerName: String,
    val className: String = "",
    val tags: List<String>? = emptyList(),
    val title: String = "",
    val description: String = "",
    val photoCount: Int = 0,
    val coverUriString: String? = null
) {
    val uris: List<Uri> get() = if (uriStrings != null && uriStrings.isNotEmpty()) {
        uriStrings.map { Uri.parse(it) }
    } else if (uriString != null) {
        listOf(Uri.parse(uriString))
    } else {
        emptyList()
    }
    
    val coverUri: Uri? get() = coverUriString?.let { Uri.parse(it) }
}

data class ViewRequest(
    val id: String = UUID.randomUUID().toString(),
    val photoId: String,
    val requesterName: String,
    val ownerName: String,
    var status: RequestStatus = RequestStatus.PENDING
)

enum class RequestStatus { PENDING, APPROVED, REJECTED }
