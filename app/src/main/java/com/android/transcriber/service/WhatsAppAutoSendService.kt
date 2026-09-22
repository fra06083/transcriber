package com.android.transcriber.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class WhatsAppAutoSendService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var isClickPending = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName == "com.whatsapp" || packageName == "com.whatsapp.w4b") {
            if (!isClickPending) {
                isClickPending = true
                handler.postDelayed({
                    tryClickSendButton()
                    isClickPending = false
                }, 800) // Delay to ensure chat layout and pre-filled text are fully rendered
            }
        }
    }

    private fun tryClickSendButton() {
        val rootNode = rootInActiveWindow ?: return

        val sendNode = findSendButtonNode(rootNode)
        if (sendNode != null && sendNode.isEnabled) {
            sendNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    private fun findSendButtonNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // 1. Check direct view ID
        val sendById = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send")
        if (!sendById.isNullOrEmpty()) {
            return sendById.firstOrNull { it.isClickable } ?: sendById.first()
        }

        val sendByBusinessId = root.findAccessibilityNodeInfosByViewId("com.whatsapp.w4b:id/send")
        if (!sendByBusinessId.isNullOrEmpty()) {
            return sendByBusinessId.firstOrNull { it.isClickable } ?: sendByBusinessId.first()
        }

        // 2. Recursive Search by Content Description / Text
        return searchNodeTree(root)
    }

    private fun searchNodeTree(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null

        val description = node.contentDescription?.toString()?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""

        if ((description.contains("invia") || description.contains("send") || description.contains("enviar") ||
             text.contains("invia") || text.contains("send") || text.contains("enviar")) && node.isClickable) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val result = searchNodeTree(child)
            if (result != null) return result
        }

        return null
    }

    override fun onInterrupt() {}
}
