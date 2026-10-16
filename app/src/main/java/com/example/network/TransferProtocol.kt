package com.example.network

import org.json.JSONArray
import org.json.JSONObject

data class HandshakeRequest(
    val senderName: String,
    val sessionId: String,
    val pin: String,
    val files: List<FileTransferMeta>
)

data class FileTransferMeta(
    val id: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val checksumSha256: String = ""
)

data class HandshakeResponse(
    val accepted: Boolean,
    val reason: String = "",
    val resumeOffsets: Map<String, Long> = emptyMap()
)

object TransferProtocol {
    const val MAGIC_HEADER = 0x514B5452 // "QKTR"
    const val PROTOCOL_VERSION = 1

    const val TYPE_HANDSHAKE_REQ = 1
    const val TYPE_HANDSHAKE_RESP = 2
    const val TYPE_FILE_HEADER = 3
    const val TYPE_FILE_CHUNK = 4
    const val TYPE_FILE_END = 5
    const val TYPE_FILE_ACK = 6
    const val TYPE_PAUSE = 7
    const val TYPE_CANCEL = 8
    const val TYPE_COMPLETE = 9

    const val CHUNK_SIZE = 64 * 1024 // 64 KB per chunk for low memory usage and smooth progress

    fun serializeHandshakeRequest(req: HandshakeRequest): String {
        val root = JSONObject()
        root.put("senderName", req.senderName)
        root.put("sessionId", req.sessionId)
        root.put("pin", req.pin)

        val filesArray = JSONArray()
        for (f in req.files) {
            val fileObj = JSONObject()
            fileObj.put("id", f.id)
            fileObj.put("name", f.name)
            fileObj.put("size", f.size)
            fileObj.put("mimeType", f.mimeType)
            fileObj.put("checksum", f.checksumSha256)
            filesArray.put(fileObj)
        }
        root.put("files", filesArray)
        return root.toString()
    }

    fun parseHandshakeRequest(jsonString: String): HandshakeRequest {
        val root = JSONObject(jsonString)
        val senderName = root.getString("senderName")
        val sessionId = root.getString("sessionId")
        val pin = root.optString("pin", "")

        val filesList = mutableListOf<FileTransferMeta>()
        val filesArray = root.getJSONArray("files")
        for (i in 0 until filesArray.length()) {
            val obj = filesArray.getJSONObject(i)
            filesList.add(
                FileTransferMeta(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    size = obj.getLong("size"),
                    mimeType = obj.optString("mimeType", "application/octet-stream"),
                    checksumSha256 = obj.optString("checksum", "")
                )
            )
        }

        return HandshakeRequest(
            senderName = senderName,
            sessionId = sessionId,
            pin = pin,
            files = filesList
        )
    }

    fun serializeHandshakeResponse(resp: HandshakeResponse): String {
        val root = JSONObject()
        root.put("accepted", resp.accepted)
        root.put("reason", resp.reason)
        val offsetsObj = JSONObject()
        resp.resumeOffsets.forEach { (k, v) -> offsetsObj.put(k, v) }
        root.put("resumeOffsets", offsetsObj)
        return root.toString()
    }

    fun parseHandshakeResponse(jsonString: String): HandshakeResponse {
        val root = JSONObject(jsonString)
        val accepted = root.getBoolean("accepted")
        val reason = root.optString("reason", "")
        val offsets = mutableMapOf<String, Long>()
        val offsetsObj = root.optJSONObject("resumeOffsets")
        offsetsObj?.keys()?.forEach { key ->
            offsets[key] = offsetsObj.getLong(key)
        }
        return HandshakeResponse(accepted, reason, offsets)
    }
}
