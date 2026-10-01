package org.fengshen.dev

import android.test.InstrumentationTestCase
import android.content.SharedPreferences

/** Emulator-only tests keep the original preferences and never log in as the user's account. */
@Suppress("DEPRECATION")
open class IsolatedGameTestCase:InstrumentationTestCase(){
    private var monitor:android.app.Instrumentation.ActivityMonitor?=null
    private val backups=mutableMapOf<SharedPreferences,Map<String,*>>()
    override fun setUp(){
        super.setUp();check(Diagnostics.emulator){"This isolated save test must not run on a real device"}
        for(name in listOf("opening-local-save","operation-a-ui","cloud-session","diagnostics","apk-update-state")){
            val p=instrumentation.targetContext.getSharedPreferences(name,0);backups[p]=p.all.toMap()
            if(name=="cloud-session")p.edit().clear().commit()
        }
        monitor=instrumentation.addMonitor(MainActivity::class.java.name,null,false)
        Diagnostics.testSession=true
    }
    override fun tearDown(){
        monitor?.lastActivity?.let{a->instrumentation.runOnMainSync{if(!a.isDestroyed)a.finish()}}
        instrumentation.waitForIdleSync();monitor?.let{instrumentation.removeMonitor(it)}
        // Only the external emulator recorder may retain this isolated fixture for
        // a real force-stop/restart check; it restores the original XML in finally.
        val keep=(instrumentation as android.test.InstrumentationTestRunner).arguments
            .getString("keepFixtureForRestart")=="true"
        for((prefs,values)in if(keep)emptyMap() else backups){
            val e=prefs.edit().clear()
            values.forEach{(k,v)->when(v){is String->e.putString(k,v);is Int->e.putInt(k,v);is Long->e.putLong(k,v);is Float->e.putFloat(k,v);is Boolean->e.putBoolean(k,v)}}
            check(e.commit())
        }
        Diagnostics.testSession=false;super.tearDown()
    }
}
