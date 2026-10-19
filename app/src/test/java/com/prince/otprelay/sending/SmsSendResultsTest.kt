package com.prince.otprelay.sending

import android.app.Activity
import android.telephony.SmsManager
import org.junit.Assert.*
import org.junit.Test

class SmsSendResultsTest {
    @Test fun onlyActualOkResultMeansSentNotDelivered() {
        assertNull(SmsSendResults.failure(Activity.RESULT_OK))
        assertEquals(SendFailure.GENERIC, SmsSendResults.failure(SmsManager.RESULT_ERROR_GENERIC_FAILURE))
        assertEquals(SendFailure.GENERIC, SmsSendResults.failure(123456))
    }

    @Test fun platformFailuresHaveActionableMessages() {
        assertEquals(SendFailure.RADIO_OFF, SmsSendResults.failure(SmsManager.RESULT_ERROR_RADIO_OFF))
        assertEquals(SendFailure.NO_SERVICE, SmsSendResults.failure(SmsManager.RESULT_ERROR_NO_SERVICE))
        assertEquals(SendFailure.LIMIT, SmsSendResults.failure(SmsManager.RESULT_ERROR_LIMIT_EXCEEDED))
        assertEquals(SendFailure.BLOCKED, SmsSendResults.failure(SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE))
    }
}
