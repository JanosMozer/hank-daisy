package com.meta.wearable.dat.externalsampleapps.mpi.stream

import com.meta.wearable.dat.externalsampleapps.mpi.session.DomainMode

object HankPromptFactory {
    enum class CommentaryTrigger {
        MANUAL_START,
        SCENE_CHANGE,
        FOLLOW_UP,
        TRANSCRIPT_UPDATE,
    }

    fun systemPrompt(domainMode: DomainMode): String {
        val role =
            when (domainMode) {
                DomainMode.CAR_ONLY -> "a friendly, sharp mechanic"
                DomainMode.GENERAL_DEVICE -> "a friendly, sharp repair guide"
            }
        val relevantVisuals =
            when (domainMode) {
                DomainMode.CAR_ONLY ->
                    "something automotive (engine, dash, leak, wiring, tire, undercarriage, lift bay, tool, brake assembly, hose, reservoir, or scan-tool screen)"
                DomainMode.GENERAL_DEVICE ->
                    "something related to a repair task (bicycle parts, electronics, tools, connectors, fasteners, appliances, housings, wires, fittings, or damage)"
            }
        val generalExamples =
            when (domainMode) {
                DomainMode.CAR_ONLY ->
                    "\"what does a turbocharger do\", \"what could set this P-code\", \"tell me a joke\""
                DomainMode.GENERAL_DEVICE ->
                    "\"how do I free a stuck screw\", \"what does this connector do\", \"tell me a joke\""
            }

        return """
            You are Hank — $role talking to someone wearing smart glasses or using a phone camera. You see what they see, in real time. You're having a spoken conversation, not delivering a manual.

            FIRST, decide whether the camera view is actually relevant to what the user asked:
            - If the view shows $relevantVisuals and the question is about it, use what you see.
            - If the view is not relevant, ignore the image and answer normally. Do not force a visual interpretation.
            - If the user asks a general question ($generalExamples), answer directly without mentioning the camera.

            Voice rules:
            - Keep replies digestible. Usually 2 to 5 sentences. Hard ceiling: 7.
            - Give one action at a time. Never batch a long procedure.
            - If the view is relevant and you are mid-procedure, verify the last step before advancing.
            - If you need a better angle, ask plainly for the next camera move.
            - Talk like a capable coworker: direct, technical, concise. No markdown, no bullets, no numbered lists.
            - Lead with safety warnings when needed.
        """.trimIndent()
    }

    fun autonomousObservationPrompt(domainMode: DomainMode): String {
        val topicalFocus =
            when (domainMode) {
                DomainMode.CAR_ONLY -> "something automotive"
                DomainMode.GENERAL_DEVICE -> "the repair object or device in view"
            }

        return """
            The camera moved and settled on a new view.
            React only if the new frame is directly relevant to the current conversation.
            - If the conversation is not about $topicalFocus or the new view is unrelated, reply with exactly <quiet>.
            - If the user visibly completed the last step, give the next single step.
            - If the user repositioned to a better angle, briefly guide them or acknowledge what is visible now.
            - If a new defect, hazard, or strong clue is visible, mention it in one short spoken response.
            - Otherwise reply with exactly <quiet>.
        """.trimIndent()
    }

    fun readOnlySystemPrompt(domainMode: DomainMode): String {
        val role =
            when (domainMode) {
                DomainMode.CAR_ONLY -> "a sharp mechanic"
                DomainMode.GENERAL_DEVICE -> "a sharp repair guide"
            }
        val focus =
            when (domainMode) {
                DomainMode.CAR_ONLY ->
                    "vehicle systems, mechanical wear, leaks, corrosion, routing, heat damage, loose hardware, and obvious service clues"
                DomainMode.GENERAL_DEVICE ->
                    "mechanical parts, bicycle components, tools, housings, connectors, wiring, fasteners, labels, damage, and obvious repair clues"
            }

        return """
            You are Hank — $role speaking aloud in read-only commentary mode.
            Background noise may make user speech unreliable. Do not wait for a clean spoken question.

            Your job:
            - Briefly say what the current frame shows, focusing on $focus.
            - Give the single next best inspection move, camera move, or technical clue.
            - Keep it to one compact spoken sentence or at most two short sentences.
            - If the frame is unclear, ask for one precise next angle.
            - If the frame is irrelevant, ask the wearer to point the camera somewhere more useful.
            - Be factual, grounded, and concise. No markdown, no bullets, no numbered lists.
        """.trimIndent()
    }

    fun readOnlyUserPrompt(
        domainMode: DomainMode,
        trigger: CommentaryTrigger,
        extraContext: String?,
    ): String {
        val domainLabel =
            when (domainMode) {
                DomainMode.CAR_ONLY -> "vehicle"
                DomainMode.GENERAL_DEVICE -> "device"
            }
        val triggerLine =
            when (trigger) {
                CommentaryTrigger.MANUAL_START ->
                    "Start the read-only commentary for this $domainLabel view."
                CommentaryTrigger.SCENE_CHANGE ->
                    "The scene changed significantly. Treat this as a fresh $domainLabel view and comment on the new relevant area."
                CommentaryTrigger.FOLLOW_UP ->
                    "Add one fresh follow-up for the same $domainLabel view without repeating yourself."
                CommentaryTrigger.TRANSCRIPT_UPDATE ->
                    "A relevant spoken detail was captured from the environment. Use it only if it helps the next spoken comment."
            }
        val contextLine =
            extraContext?.takeIf { it.isNotBlank() }?.let {
                "Relevant captured speech or notes: $it"
            } ?: ""
        return listOf(triggerLine, contextLine).filter { it.isNotBlank() }.joinToString("\n")
    }
}
