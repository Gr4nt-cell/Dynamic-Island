package com.gr4nt.dynamicisland

/** A notification being 'ongoing' also covers dialing. Only connection evidence starts time. */
object CallTiming {
    fun incoming(callType: Int, hasAnswerAction: Boolean, text: String): Boolean =
        callType==1 || (callType==0 && (hasAnswerAction || Regex("(?i)\\bincoming\\b").containsMatchIn(text)))
    fun waiting(text: String): Boolean = Regex("(?i)\\b(calling|dialing|dialling|ringing|connecting|outgoing|trying)\\b|waiting for (an? )?answer").containsMatchIn(text)
    fun connected(incoming: Boolean, chronometer: Boolean, text: String): Boolean =
        !incoming && !waiting(text) && (chronometer || Regex("(?i)\\b(connected|ongoing|active call|call in progress|in call|on hold)\\b").containsMatchIn(text))
    fun start(incoming: Boolean, connected: Boolean, knownStart: Long?, observedWaiting: Boolean, sourceStart: Long?, now: Long): Long? = when {
        incoming -> null
        knownStart!=null -> knownStart
        !connected -> null
        // Some dialers retain the original dialing/ringing timestamp after answer.
        observedWaiting -> now
        else -> sourceStart ?: now
    }
}
