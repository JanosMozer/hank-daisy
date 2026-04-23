/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.hankdaisy.session

import com.meta.wearable.dat.externalsampleapps.hankdaisy.stream.ChatMessage

/**
 * A completed Hank session: a conversation plus an auto-derived title and
 * short description for the home list.
 *
 * [orderId] is set when the session was started from inside an open
 * repair order; null for free-form Convos/Chats sessions. One-way link —
 * orders never mutate when sessions are added; the orders list derives
 * "my sessions" with a filter pass.
 */
data class Session(
    val id: String,
    val createdAt: Long,
    val title: String,
    val description: String,
    val messages: List<ChatMessage>,
    val orderId: String? = null,
) {
    companion object {
        fun from(messages: List<ChatMessage>, orderId: String? = null): Session {
            val firstUser = messages.firstOrNull { it.role == ChatMessage.Role.USER }
            val firstHank = messages.firstOrNull { it.role == ChatMessage.Role.ASSISTANT }
            val title =
                firstUser?.text?.trim()?.take(60)?.let { if (it.length >= 60) "$it…" else it }
                    ?: "Untitled session"
            val description =
                firstHank?.text?.trim()?.take(120)?.let { if (it.length >= 120) "$it…" else it }
                    ?: "No reply captured."
            val createdAt =
                messages.firstOrNull()?.timestamp ?: System.currentTimeMillis()
            return Session(
                id = "session-${createdAt}-${messages.size}",
                createdAt = createdAt,
                title = title,
                description = description,
                messages = messages,
                orderId = orderId,
            )
        }
    }
}
