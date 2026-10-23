package com.yuriy.diapason

/**
 * The application Robolectric boots for every test (see `robolectric.properties`). Still a
 * [MainApp], since the ViewModels cast to it for the repository, but without Firebase:
 * Analytics' measurement thread throws a VerifyError on the JVM that kills the test process.
 * [com.yuriy.diapason.analytics.AppAnalytics] no-ops while uninitialized.
 */
class TestMainApp : MainApp() {
    override fun initFirebase() = Unit
}
