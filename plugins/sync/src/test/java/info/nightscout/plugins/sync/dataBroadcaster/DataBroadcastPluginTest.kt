package info.nightscout.plugins.sync.dataBroadcaster

import app.aaps.shared.tests.BundleMock
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.database.entities.GlucoseValue
import info.nightscout.database.entities.TemporaryBasal
import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.interfaces.aps.AutosensDataStore
import info.nightscout.interfaces.aps.Loop
import info.nightscout.interfaces.iob.CobInfo
import info.nightscout.interfaces.iob.GlucoseStatus
import info.nightscout.interfaces.iob.GlucoseStatusProvider
import info.nightscout.interfaces.iob.InMemoryGlucoseValue
import info.nightscout.interfaces.iob.IobTotal
import info.nightscout.interfaces.nsclient.ProcessedDeviceStatusData
import info.nightscout.interfaces.profile.DefaultValueHelper
import info.nightscout.interfaces.pump.PumpEnactResult
import info.nightscout.interfaces.receivers.ReceiverStatusStore
import info.nightscout.rx.events.EventOverviewBolusProgress
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mock
import org.mockito.Mockito

internal class DataBroadcastPluginTest : TestBaseWithProfile() {

    @Mock lateinit var defaultValueHelper: DefaultValueHelper
    @Mock lateinit var loop: Loop
    @Mock lateinit var receiverStatusStore: ReceiverStatusStore
    @Mock lateinit var glucoseStatusProvider: GlucoseStatusProvider
    @Mock lateinit var autosensDataStore: AutosensDataStore
    @Mock lateinit var processedDeviceStatusData: ProcessedDeviceStatusData

    private lateinit var sut: DataBroadcastPlugin

    private val injector = HasAndroidInjector { AndroidInjector { } }

    @BeforeEach
    fun setUp() {
        sut = DataBroadcastPlugin(
            injector, aapsLogger, rh, aapsSchedulers, context, dateUtil, fabricPrivacy, rxBus, iobCobCalculator, profileFunction, defaultValueHelper, processedDeviceStatusData,
            loop, activePlugin, receiverStatusStore, config, glucoseStatusProvider, decimalFormatter
        )
        Mockito.`when`(iobCobCalculator.ads).thenReturn(autosensDataStore)
        Mockito.`when`(autosensDataStore.lastBg()).thenReturn(InMemoryGlucoseValue(1000, 100.0, sourceSensor = GlucoseValue.SourceSensor.UNKNOWN))
        Mockito.`when`(profileFunction.getProfile()).thenReturn(validProfile)
        Mockito.`when`(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
        Mockito.`when`(profileFunction.getProfileName()).thenReturn("TestProfile")
        Mockito.`when`(iobCobCalculator.calculateIobFromBolus()).thenReturn(IobTotal(System.currentTimeMillis()))
        Mockito.`when`(iobCobCalculator.getCobInfo("broadcast")).thenReturn(CobInfo(1000, 100.0, 10.0))
        Mockito.`when`(iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended()).thenReturn(IobTotal(System.currentTimeMillis()))
        Mockito.`when`(iobCobCalculator.getTempBasalIncludingConvertedExtended(anyLong()))
            .thenReturn(TemporaryBasal(timestamp = 1000, duration = 60000, isAbsolute = true, rate = 1.0, type = TemporaryBasal.Type.NORMAL))
        Mockito.`when`(processedDeviceStatusData.uploaderStatus).thenReturn("100%")
        Mockito.`when`(loop.lastRun).thenReturn(Loop.LastRun().also {
            it.lastTBREnact = 1000
            it.tbrSetByPump = PumpEnactResult(injector).success(true).enacted(true)
        }
        )
        Mockito.`when`(activePlugin.activePump).thenReturn(testPumpPlugin)
        Mockito.`when`(glucoseStatusProvider.glucoseStatusData).thenReturn(GlucoseStatus(100.0))
        Mockito.`when`(processedDeviceStatusData.openAPSData).thenReturn(ProcessedDeviceStatusData.OpenAPSData().also {
            it.clockSuggested = 1000L
            it.suggested = JSONObject()
            it.clockEnacted = 1000L
            it.enacted = JSONObject()
        })
    }

    @Test
    fun prepareDataTestAPS() {
        Mockito.`when`(config.APS).thenReturn(true)
        val event = EventOverviewBolusProgress.also {
            it.status = "Some status"
            it.percent = 100
        }
        val bundle = BundleMock.mock()
        sut.prepareData(event, bundle)
        assertThat(bundle.containsKey("progressPercent")).isTrue()
        assertThat(bundle.containsKey("progressStatus")).isTrue()
        assertThat(bundle.containsKey("glucoseMgdl")).isTrue()
        assertThat(bundle.containsKey("glucoseTimeStamp")).isTrue()
        assertThat(bundle.containsKey("units")).isTrue()
        assertThat(bundle.containsKey("slopeArrow")).isTrue()
        assertThat(bundle.containsKey("deltaMgdl")).isTrue()
        assertThat(bundle.containsKey("avgDeltaMgdl")).isTrue()
        assertThat(bundle.containsKey("high")).isTrue()
        assertThat(bundle.containsKey("low")).isTrue()
        assertThat(bundle.containsKey("bolusIob")).isTrue()
        assertThat(bundle.containsKey("basalIob")).isTrue()
        assertThat(bundle.containsKey("iob")).isTrue()
        assertThat(bundle.containsKey("cob")).isTrue()
        assertThat(bundle.containsKey("futureCarbs")).isTrue()
        assertThat(bundle.containsKey("phoneBattery")).isTrue()
        assertThat(bundle.containsKey("rigBattery")).isTrue()
        assertThat(bundle.containsKey("suggestedTimeStamp")).isTrue()
        assertThat(bundle.containsKey("suggested")).isTrue()
        assertThat(bundle.containsKey("enactedTimeStamp")).isTrue()
        assertThat(bundle.containsKey("enacted")).isTrue()
        assertThat(bundle.containsKey("basalTimeStamp")).isTrue()
        assertThat(bundle.containsKey("baseBasal")).isTrue()
        assertThat(bundle.containsKey("profile")).isTrue()
        assertThat(bundle.containsKey("tempBasalStart")).isTrue()
        assertThat(bundle.containsKey("tempBasalDurationInMinutes")).isTrue()
        assertThat(bundle.containsKey("tempBasalString")).isTrue()
        assertThat(bundle.containsKey("pumpTimeStamp")).isTrue()
        assertThat(bundle.containsKey("pumpBattery")).isTrue()
        assertThat(bundle.containsKey("pumpReservoir")).isTrue()
        assertThat(bundle.containsKey("pumpStatus")).isTrue()
    }

    @Test
    fun prepareDataTestAAPSClient() {
        Mockito.`when`(config.APS).thenReturn(false)
        val event = EventOverviewBolusProgress.also {
            it.status = "Some status"
            it.percent = 100
        }
        val bundle = BundleMock.mock()
        sut.prepareData(event, bundle)
        assertThat(bundle.containsKey("progressPercent")).isTrue()
        assertThat(bundle.containsKey("progressStatus")).isTrue()
        assertThat(bundle.containsKey("glucoseMgdl")).isTrue()
        assertThat(bundle.containsKey("glucoseTimeStamp")).isTrue()
        assertThat(bundle.containsKey("units")).isTrue()
        assertThat(bundle.containsKey("slopeArrow")).isTrue()
        assertThat(bundle.containsKey("deltaMgdl")).isTrue()
        assertThat(bundle.containsKey("avgDeltaMgdl")).isTrue()
        assertThat(bundle.containsKey("high")).isTrue()
        assertThat(bundle.containsKey("low")).isTrue()
        assertThat(bundle.containsKey("bolusIob")).isTrue()
        assertThat(bundle.containsKey("basalIob")).isTrue()
        assertThat(bundle.containsKey("iob")).isTrue()
        assertThat(bundle.containsKey("cob")).isTrue()
        assertThat(bundle.containsKey("futureCarbs")).isTrue()
        assertThat(bundle.containsKey("phoneBattery")).isTrue()
        assertThat(bundle.containsKey("rigBattery")).isTrue()
        assertThat(bundle.containsKey("suggestedTimeStamp")).isTrue()
        assertThat(bundle.containsKey("suggested")).isTrue()
        assertThat(bundle.containsKey("enactedTimeStamp")).isTrue()
        assertThat(bundle.containsKey("enacted")).isTrue()
        assertThat(bundle.containsKey("basalTimeStamp")).isTrue()
        assertThat(bundle.containsKey("baseBasal")).isTrue()
        assertThat(bundle.containsKey("profile")).isTrue()
        assertThat(bundle.containsKey("tempBasalStart")).isTrue()
        assertThat(bundle.containsKey("tempBasalDurationInMinutes")).isTrue()
        assertThat(bundle.containsKey("tempBasalString")).isTrue()
        assertThat(bundle.containsKey("pumpTimeStamp")).isTrue()
        assertThat(bundle.containsKey("pumpBattery")).isTrue()
        assertThat(bundle.containsKey("pumpReservoir")).isTrue()
        assertThat(bundle.containsKey("pumpStatus")).isTrue()
    }
}
