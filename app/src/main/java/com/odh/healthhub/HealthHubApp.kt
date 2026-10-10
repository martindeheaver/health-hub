package com.odh.healthhub

import android.app.Application
import com.odh.healthhub.ai.Analyzer
import com.odh.healthhub.data.HealthRepository
import com.odh.healthhub.data.Store
import com.odh.healthhub.work.AnalysisWorker

class HealthHubApp : Application() {
    lateinit var repo: HealthRepository
    lateinit var store: Store
    lateinit var analyzer: Analyzer

    override fun onCreate() {
        super.onCreate()
        repo = HealthRepository(this)
        store = Store(this)
        analyzer = Analyzer(repo, store)
        AnalysisWorker.createChannel(this)
        AnalysisWorker.schedule(this)
    }
}
