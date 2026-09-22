package com.classcue.kexing.engine

import android.app.Activity
import android.content.Context
import com.classcue.kexing.model.AppSettings
import com.huawei.wearengine.HiWear
import com.huawei.wearengine.auth.AuthCallback
import com.huawei.wearengine.auth.Permission
import com.huawei.wearengine.p2p.Message
import com.huawei.wearengine.p2p.SendCallback

/** Wear Engine peer-to-peer channel for the Lite Wearable companion. */
object WearEngineBridge {
    fun sendCue(
        context: Context,
        eventId: Long,
        title: String,
        question: String,
        settings: AppSettings,
        onStatus: (String) -> Unit = {}
    ) {
        if (!settings.wearEngineEnabled) return
        send(context, settings, payload(eventId, KIND_CUE, title, question = question,
            vibrate = settings.watchVibrationEnabled), onStatus)
    }

    fun sendAnswer(
        context: Context,
        eventId: Long,
        tier: String,
        model: String,
        answer: String,
        settings: AppSettings,
        onStatus: (String) -> Unit = {}
    ) {
        if (!settings.wearEngineEnabled) return
        send(context, settings, payload(eventId, KIND_ANSWER, "课醒 · ${tier}答案",
            tier = tier, model = model, answer = answer,
            vibrate = settings.watchVibrationEnabled), onStatus)
    }

    fun authorizeAndSendTest(activity: Activity, settings: AppSettings, onStatus: (String) -> Unit) {
        if (!hasUsablePeerIdentity(settings)) {
            onStatus("请先填写正式 HAP 的签名 SHA-256 指纹")
            return
        }
        onStatus("正在检查华为穿戴设备授权…")
        runCatching {
            HiWear.getAuthClient(activity).checkPermission(Permission.DEVICE_MANAGER)
                .addOnSuccessListener { granted ->
                    if (granted == true) sendTest(activity, settings, onStatus)
                    else {
                        onStatus("请在弹窗中允许管理穿戴设备…")
                        requestDeviceManagerPermission(activity, settings, onStatus)
                    }
                }
                .addOnFailureListener { error ->
                    onStatus(permissionFailure("Wear Engine 权限检查失败：", error))
                }
        }.onFailure { error -> onStatus(permissionFailure("Wear Engine 权限检查失败：", error)) }
    }

    private fun requestDeviceManagerPermission(
        activity: Activity, settings: AppSettings, onStatus: (String) -> Unit
    ) {
        runCatching {
            HiWear.getAuthClient(activity).requestPermission(object : AuthCallback {
                override fun onOk(permissions: Array<out Permission>?) {
                    sendTest(activity, settings, onStatus)
                }
                override fun onCancel() { onStatus("已取消 Wear Engine 授权") }
            }, Permission.DEVICE_MANAGER).addOnFailureListener { error ->
                onStatus(permissionFailure("Wear Engine 授权失败：", error))
            }
        }.onFailure { error -> onStatus(permissionFailure("Wear Engine 授权失败：", error)) }
    }

    private fun sendTest(activity: Activity, settings: AppSettings, onStatus: (String) -> Unit) {
        onStatus("授权完成，正在寻找已连接手表…")
        sendCue(activity, System.currentTimeMillis(), "课醒直连测试",
            "如果手表显示本条消息，说明 Wear Engine 直连可用。", settings, onStatus)
    }

    internal fun payload(
        eventId: Long,
        kind: String,
        title: String,
        question: String = "",
        tier: String = "",
        model: String = "",
        answer: String = "",
        vibrate: Boolean = true
    ): String {
        fun field(name: String, value: String): String =
            "\"${jsonEscape(name)}\":\"${jsonEscape(value)}\""
        return buildString {
            append('{')
            append("\"schema\":").append(SCHEMA_VERSION).append(',')
            append(field("eventId", eventId.toString())).append(',')
            append(field("kind", kind)).append(',')
            append(field("title", title.take(MAX_TITLE_CHARS))).append(',')
            append(field("question", question.trim().take(MAX_QUESTION_CHARS))).append(',')
            append(field("tier", tier.take(MAX_TIER_CHARS))).append(',')
            append(field("model", model.take(MAX_MODEL_CHARS))).append(',')
            append(field("answer", answer.trim().take(MAX_ANSWER_CHARS))).append(',')
            append("\"vibrate\":").append(vibrate).append(',')
            append("\"sentAt\":").append(System.currentTimeMillis())
            append('}')
        }
    }

    private fun jsonEscape(value: String): String = buildString(value.length) {
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) {
                    append("\\u%04x".format(character.code))
                } else append(character)
            }
        }
    }

    private fun send(context: Context, settings: AppSettings, payload: String, onStatus: (String) -> Unit) {
        val peerPackage = settings.wearEngineWatchPackage.trim()
        val peerFingerprint = normalizeFingerprint(settings.wearEngineWatchFingerprint)
        if (peerPackage.isBlank() || peerFingerprint.length != 64 || peerFingerprint.all { it == '0' }) {
            onStatus("正式版尚未配置有效的手表包名和签名指纹")
            return
        }
        runCatching {
            HiWear.getDeviceClient(context).bondedDevices
                .addOnSuccessListener { devices ->
                    val device = devices.firstOrNull { it.isConnected }
                    if (device == null) {
                        onStatus("未找到已连接的华为穿戴设备")
                        return@addOnSuccessListener
                    }
                    val client = HiWear.getP2pClient(context)
                        .setPeerPkgName(peerPackage)
                        .setPeerFingerPrint(peerFingerprint)
                    val message = Message.Builder()
                        .setPayload(payload.toByteArray(Charsets.UTF_8))
                        .setDescription("KEXING/$SCHEMA_VERSION")
                        .setEnableEncrypt(true)
                        .build()
                    client.send(device, message, object : SendCallback {
                        override fun onSendResult(resultCode: Int) {
                            onStatus("手表已返回发送状态码 $resultCode")
                        }
                        override fun onSendProgress(progress: Long) = Unit
                    }).addOnSuccessListener {
                        onStatus("已提交到 ${device.name.ifBlank { "华为手表" }}")
                    }.addOnFailureListener { error ->
                        onStatus("发送到手表失败：${safeMessage(error)}")
                    }
                }
                .addOnFailureListener { error -> onStatus("读取穿戴设备失败：${safeMessage(error)}") }
        }.onFailure { error -> onStatus("Wear Engine 不可用：${safeMessage(error)}") }
    }

    private fun hasUsablePeerIdentity(settings: AppSettings): Boolean {
        val fingerprint = normalizeFingerprint(settings.wearEngineWatchFingerprint)
        return settings.wearEngineWatchPackage.isNotBlank() && fingerprint.length == 64 &&
            fingerprint.any { it != '0' }
    }

    private fun normalizeFingerprint(value: String): String =
        value.trim().replace(Regex("[^0-9A-Fa-f]"), "").uppercase()

    private fun permissionFailure(prefix: String, error: Throwable): String {
        val message = safeMessage(error)
        return if (message.contains("Scope unauthorized", ignoreCase = true)) {
            "开发者后台尚未审批 DEVICE_MANAGER 权限（Scope unauthorized）"
        } else prefix + message
    }

    private fun safeMessage(error: Throwable): String =
        error.message?.take(120).orEmpty().ifBlank { error.javaClass.simpleName }

    const val KIND_CUE = "cue"
    const val KIND_ANSWER = "answer"
    private const val SCHEMA_VERSION = 1
    private const val MAX_TITLE_CHARS = 80
    private const val MAX_QUESTION_CHARS = 1_500
    private const val MAX_ANSWER_CHARS = 3_500
    private const val MAX_MODEL_CHARS = 80
    private const val MAX_TIER_CHARS = 16
}
