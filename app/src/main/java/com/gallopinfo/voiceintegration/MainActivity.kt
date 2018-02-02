package com.gallopinfo.voiceintegration

import ai.kitt.snowboy.AppResCopy
import ai.kitt.snowboy.MsgEnum
import ai.kitt.snowboy.audio.AudioDataSaver
import ai.kitt.snowboy.audio.PlaybackThread
import ai.kitt.snowboy.audio.RecordingThread
import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.*
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.text.Html
import android.view.View
import android.widget.*
import com.microsoft.bing.speech.SpeechClientStatus
import com.microsoft.cognitiveservices.speechrecognition.*
import kotlinx.android.synthetic.main.wakeup_act.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() ,  ISpeechRecognitionServerEvents{
    val CODE_FOR_WRITE_PERMISSION:Int = 10010


    private var record_button: Button? = null
    private var play_button: Button? = null
    private var log: TextView? = null
    private var logView: ScrollView? = null
    internal var strLog:String? = null

    private var preVolume = -1
    private var activeTimes:Long = 0

    private var recordingThread: RecordingThread? = null
    private var playbackThread: PlaybackThread? = null


    internal var m_waitSeconds = 0
    internal var dataClient: DataRecognitionClient? = null
    internal var micClient: MicrophoneRecognitionClient? = null
    internal var isReceivedResponse = FinalResponseStatus.NotReceived


    public override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.wakeup_act)
        setUI()
        setProperVolume()

        if(judageHasPermission()) {
            AppResCopy.copyResFromAssetsToSD(this)
        }

        activeTimes = 0
        recordingThread = RecordingThread(handle, AudioDataSaver())
        playbackThread = PlaybackThread()

        // 启动就自动线程
        startRecording()


        // 关于语音翻译的
        if (getString(R.string.primaryKey).startsWith("Please")) {
            AlertDialog.Builder(this)
                    .setTitle(getString(R.string.add_subscription_key_tip_title))
                    .setMessage(getString(R.string.add_subscription_key_tip))
                    .setCancelable(false)
                    .show()
        }


    }

    internal fun showToast(msg:CharSequence) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun setUI() {
        record_button = findViewById<View>(R.id.btn_test1) as Button
        record_button!!.setOnClickListener(record_button_handle)
                record_button!!.isEnabled = true

        play_button = findViewById<View>(R.id.btn_test2) as Button
        play_button!!.setOnClickListener(play_button_handle)
                play_button!!.isEnabled = true

        log = findViewById<View>(R.id.log) as TextView
        logView = findViewById<View>(R.id.logView) as ScrollView
    }

    private fun setMaxVolume() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        preVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        updateLog(" ----> preVolume = " + preVolume, "green")
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        updateLog(" ----> maxVolume = " + maxVolume, "green")
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        updateLog(" ----> currentVolume = " + currentVolume, "green")
    }

    private fun setProperVolume() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        preVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        updateLog(" ----> preVolume = " + preVolume, "green")
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        updateLog(" ----> maxVolume = " + maxVolume, "green")
        val properVolume = (maxVolume.toFloat() * 0.2).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, properVolume, 0)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        updateLog(" ----> currentVolume = " + currentVolume, "green")
    }

    private fun restoreVolume() {
        if (preVolume >= 0)
        {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager!!.setStreamVolume(AudioManager.STREAM_MUSIC, preVolume, 0)
        updateLog(" ----> set preVolume = " + preVolume, "green")
        val currentVolume = audioManager!!.getStreamVolume(AudioManager.STREAM_MUSIC)
        updateLog(" ----> currentVolume = " + currentVolume, "green")
        }
    }

    private fun startRecording() {
        recordingThread!!.startRecording()
        updateLog(" ----> recording started ...", "green")
        record_button!!.setText(R.string.btn1_stop)
    }

    private fun stopRecording() {
        recordingThread!!.stopRecording()
        updateLog(" ----> recording stopped ", "green")
        record_button!!.setText(R.string.btn1_start)
    }

    private fun startPlayback() {
        updateLog(" ----> playback started ...", "green")
        play_button!!.setText(R.string.btn2_stop)
 // (new PcmPlayer()).playPCM();
        playbackThread!!.startPlayback()
    }

    private fun stopPlayback() {
        updateLog(" ----> playback stopped ", "green")
        play_button!!.setText(R.string.btn2_start)
        playbackThread!!.stopPlayback()
    }

    private fun sleep() {
        try
        {
            Thread.sleep(500)
        }
        catch (e:Exception) {}

    }

    private val record_button_handle = object :android.view.View.OnClickListener // @Override
    {
        override fun onClick(v: View?) {
            if (record_button!!.text == resources.getString(R.string.btn1_start)) {
                stopPlayback()
                sleep()
                startRecording()
            } else {
                stopRecording()
                sleep()
            }
        }

    }

    private val play_button_handle = object: View.OnClickListener {
        override fun onClick(arg0: View) {
            if (play_button!!.text == resources.getString(R.string.btn2_start))
            {
                stopRecording()
                sleep()
                startPlayback()
            }
            else
            {
                stopPlayback()
            }
        }
    }

    var handle: Handler = @SuppressLint("HandlerLeak")
    object: Handler() {
        override fun handleMessage(msg: Message) {
            val message = MsgEnum.getMsgEnum(msg.what)
            when (message) {
            MsgEnum.MSG_ACTIVE -> {
            activeTimes++
            updateLog(" ----> Detected $activeTimes times", "green")
             // Toast.makeText(Demo.this, "Active "+activeTimes, Toast.LENGTH_SHORT).show();

                // 关闭唤醒词检测线程
                stopRecording()

                // 识别到了唤醒词
                StartButton_Click()
                showToast("Active " + activeTimes)
            }
            MsgEnum.MSG_INFO -> updateLog(" ----> " + message)
            MsgEnum.MSG_VAD_SPEECH -> updateLog(" ----> normal voice", "blue")
            MsgEnum.MSG_VAD_NOSPEECH -> updateLog(" ----> no speech", "blue")
            MsgEnum.MSG_ERROR -> updateLog(" ----> " + msg.toString(), "red")
            else -> super.handleMessage(msg)
            }
        }
    }

      fun updateLog(text:String) {
        log!!.post(object:Runnable {
        override fun run() {
            if (currLogLineNum >= MAX_LOG_LINE_NUM)
            {
            val st = strLog!!.indexOf("<br>")
            strLog = strLog!!.substring(st + 4)
            }
            else
            {
            currLogLineNum++
            }
            val str = "<font color='white'>$text</font><br>"
            strLog = if ((strLog == null || strLog!!.length == 0)) str else strLog!! + str
            log!!.text = Html.fromHtml(strLog)
            }
        })
        logView!!.post(object:Runnable {
            override fun run() {
                logView!!.fullScroll(ScrollView.FOCUS_DOWN)
            }
        })
    }

    internal var MAX_LOG_LINE_NUM = 200
    internal var currLogLineNum = 0

    fun updateLog(text:String, color:String) {
        log!!.post(object:Runnable {
            override fun run() {
                if (currLogLineNum >= MAX_LOG_LINE_NUM)
                {
                val st = strLog!!.indexOf("<br>")
                strLog = strLog!!.substring(st + 4)
                }
                else
                {
                currLogLineNum++
                }
                val str = "<font color='$color'>$text</font><br>"
                strLog = if ((strLog == null || strLog!!.length == 0)) str else strLog!! + str
                log!!.text = Html.fromHtml(strLog)
            }
        })

        logView!!.post(object:Runnable {
            override fun run() {
                logView!!.fullScroll(ScrollView.FOCUS_DOWN)
            }
        })
    }

    private fun emptyLog() {
        strLog = null
        log!!.text = ""
    }

    public override fun onDestroy() {
        restoreVolume()
        recordingThread!!.stopRecording()
        super.onDestroy()
    }


    fun judageHasPermission():Boolean{
        var hasPermission:Boolean = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val hasWriteContactsPermission =   checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )

            if (hasWriteContactsPermission != PackageManager.PERMISSION_GRANTED) {
                hasPermission = false
                ActivityCompat.requestPermissions(this, arrayOf(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.RECORD_AUDIO
                ),
                        CODE_FOR_WRITE_PERMISSION)
            }else{

            }

        } else {
//            TODO("VERSION.SDK_INT < M")
        }
        return hasPermission
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CODE_FOR_WRITE_PERMISSION) {
            if (permissions[0] == Manifest.permission.WRITE_EXTERNAL_STORAGE && grantResults[0] == PackageManager.PERMISSION_GRANTED) { //用户同意使用write

                AppResCopy.copyResFromAssetsToSD(this)

            } else {
                //用户不同意，自行处理即可
                Toast.makeText(this, "用户未授权", Toast.LENGTH_SHORT).show()
            }
        }
    }


    enum class FinalResponseStatus {
        NotReceived, OK, Timeout
    }

    /**
     * Gets the primary subscription key
     */
    fun getPrimaryKey(): String {
        return this.getString(R.string.primaryKey)
    }

    /**
     * Gets the LUIS application identifier.
     * @return The LUIS application identifier.
     */
    private fun getLuisAppId(): String {
        return this.getString(R.string.luisAppID)
    }

    /**
     * Gets the LUIS subscription identifier.
     * @return The LUIS subscription identifier.
     */
    private fun getLuisSubscriptionID(): String {
        return this.getString(R.string.luisSubscriptionID)
    }

    /**
     * Gets a value indicating whether or not to use the microphone.
     * @return true if [use microphone]; otherwise, false.
     */
    private fun getUseMicrophone(): Boolean {
        return true
    }

    /**
     * Gets a value indicating whether LUIS results are desired.
     * @return true if LUIS results are to be returned otherwise, false.
     */
    private fun getWantIntent(): Boolean {
        // 是否通过intent来解析语义
        return true
    }

    /**
     * Gets the current speech recognition mode.
     * @return The speech recognition mode.
     */
    private fun getMode(): SpeechRecognitionMode {
        // 短距离识别
        return SpeechRecognitionMode.ShortPhrase

        // 长距离语音识别
//        return SpeechRecognitionMode.LongDictation
    }

    /**
     * Gets the default locale.
     * @return The default locale.
     */
    private fun getDefaultLocale(): String {
        //        return "en-us";
        // 中文
        return "zh-CN"
    }

    /**
     * Gets the short wave file path.
     * @return The short wave file.
     */
    private fun getShortWaveFile(): String {
        return "whatstheweatherlike.wav"
    }

    /**
     * Gets the long wave file path.
     * @return The long wave file.
     */
    private fun getLongWaveFile(): String {
        return "batman.wav"
    }

    /**
     * Gets the Cognitive Service Authentication Uri.
     * @return The Cognitive Service Authentication Uri.  Empty if the global default is to be used.
     */
    private fun getAuthenticationUri(): String {
        return this.getString(R.string.authenticationUri)
    }


    /**
     * Handles the Click event of the _startButton control.
     */
    private fun StartButton_Click() {
        this.m_waitSeconds = if (this.getMode() == SpeechRecognitionMode.ShortPhrase) 20 else 200

        this.LogRecognitionStart()

        if (this.getUseMicrophone()) {
            if (this.micClient == null) {
                if (this.getWantIntent()) {
                    this.WriteLine("--- Start microphone dictation with Intent detection ----")

                    this.micClient = SpeechRecognitionServiceFactory.createMicrophoneClientWithIntent(
                            this,
                            this.getDefaultLocale(),
                            this,
                            this.getPrimaryKey(),
                            this.getLuisAppId(),
                            this.getLuisSubscriptionID())
                } else {
                    this.micClient = SpeechRecognitionServiceFactory.createMicrophoneClient(
                            this,
                            this.getMode(),
                            this.getDefaultLocale(),
                            this,
                            this.getPrimaryKey())
                }

                this.micClient!!.setAuthenticationUri(this.getAuthenticationUri())
            }

            this.micClient!!.startMicAndRecognition()
        } else {
            if (null == this.dataClient) {
                if (this.getWantIntent()) {
                    this.dataClient = SpeechRecognitionServiceFactory.createDataClientWithIntent(
                            this,
                            this.getDefaultLocale(),
                            this,
                            this.getPrimaryKey(),
                            this.getLuisAppId(),
                            this.getLuisSubscriptionID())
                } else {
                    this.dataClient = SpeechRecognitionServiceFactory.createDataClient(
                            this,
                            this.getMode(),
                            this.getDefaultLocale(),
                            this,
                            this.getPrimaryKey())
                }

                this.dataClient!!.setAuthenticationUri(this.getAuthenticationUri())
            }

            this.SendAudioHelper(if (this.getMode() == SpeechRecognitionMode.ShortPhrase) this.getShortWaveFile() else this.getLongWaveFile())
        }
    }

    /**
     * Logs the recognition start.
     */
    private fun LogRecognitionStart() {
        val recoSource: String
        if (this.getUseMicrophone()) {
            recoSource = "microphone"
        } else if (this.getMode() == SpeechRecognitionMode.ShortPhrase) {
            recoSource = "short wav file"
        } else {
            recoSource = "long wav file"
        }

        this.WriteLine("\n--- Start speech recognition using " + recoSource + " with " + this.getMode() + " mode in " + this.getDefaultLocale() + " language ----\n\n")
    }

    private fun SendAudioHelper(filename: String) {
        val doDataReco = RecognitionTask(this.dataClient!!, this.getMode(), filename)
        try {
            doDataReco.execute().get(m_waitSeconds.toLong(), TimeUnit.SECONDS)
        } catch (e: Exception) {
            doDataReco.cancel(true)
            isReceivedResponse = FinalResponseStatus.Timeout
        }

    }

    override fun onFinalResponseReceived(response: RecognitionResult) {
        val isFinalDicationMessage = this.getMode() == SpeechRecognitionMode.LongDictation && (response.RecognitionStatus == RecognitionStatus.EndOfDictation || response.RecognitionStatus == RecognitionStatus.DictationEndSilenceTimeout)
        if (null != this.micClient && this.getUseMicrophone() && (this.getMode() == SpeechRecognitionMode.ShortPhrase || isFinalDicationMessage)) {
            // we got the final result, so it we can end the mic reco.  No need to do this
            // for dataReco, since we already called endAudio() on it as soon as we were done
            // sending all the data.
            this.micClient!!.endMicAndRecognition()
        }

        if (isFinalDicationMessage) {
            this.isReceivedResponse = FinalResponseStatus.OK
        }

        if (!isFinalDicationMessage) {
            this.WriteLine("********* Final n-BEST Results *********")
            for (i in response.Results.indices) {
                this.WriteLine("[" + i + "]" + " Confidence=" + response.Results[i].Confidence +
                        " Text=\"" + response.Results[i].DisplayText + "\"")
            }

            this.WriteLine()
        }

        // 继续开启唤醒词检测线程
        startRecording()

    }

    /**
     * Called when a final response is received and its intent is parsed
     */
    override fun onIntentReceived(payload: String) {
        this.WriteLine("--- Intent received by onIntentReceived() ---")
        this.WriteLine(payload)
        this.WriteLine()
    }

    override fun onPartialResponseReceived(response: String) {
        this.WriteLine("--- Partial result received by onPartialResponseReceived() ---")
        this.WriteLine(response)
        this.WriteLine()
    }

    override fun onError(errorCode: Int, response: String) {
        this.WriteLine("--- Error received by onError() ---")
        this.WriteLine("Error code: " + SpeechClientStatus.fromInt(errorCode) + " " + errorCode)
        this.WriteLine("Error text: " + response)
        this.WriteLine()
    }

    /**
     * Called when the microphone status has changed.
     * @param recording The current recording state
     */
    override fun onAudioEvent(recording: Boolean) {
        this.WriteLine("--- Microphone status change received by onAudioEvent() ---")
        this.WriteLine("********* Microphone status: $recording *********")
        if (recording) {
            this.WriteLine("Please start speaking.")
        }

        WriteLine()
        if (!recording) {
            this.micClient!!.endMicAndRecognition()
        }
    }

    /**
     * Writes the line.
     */
    private fun WriteLine() {
        this.WriteLine("")
    }

    /**
     * Writes the line.
     * @param text The line to write.
     */
    private fun WriteLine(text: String) {
        this._logText.append(text + "\n")
    }

    /**
     * Handles the Click event of the RadioButton control.
     * @param rGroup The radio grouping.
     * @param checkedId The checkedId.
     */
    private fun RadioButton_Click(rGroup: RadioGroup, checkedId: Int) {
        // Reset everything
        if (this.micClient != null) {
            this.micClient!!.endMicAndRecognition()
            try {
                this.micClient!!.finalize()
            } catch (throwable: Throwable) {
                throwable.printStackTrace()
            }

            this.micClient = null
        }

        if (this.dataClient != null) {
            try {
                this.dataClient!!.finalize()
            } catch (throwable: Throwable) {
                throwable.printStackTrace()
            }

            this.dataClient = null
        }

    }

    /*
     * Speech recognition with data (for example from a file or audio source).
     * The data is broken up into buffers and each buffer is sent to the Speech Recognition Service.
     * No modification is done to the buffers, so the user can apply their
     * own VAD (Voice Activation Detection) or Silence Detection
     *
     * @param dataClient
     * @param recoMode
     * @param filename
     */
    private inner class RecognitionTask internal constructor(internal var dataClient: DataRecognitionClient, internal var recoMode: SpeechRecognitionMode, internal var filename: String) : AsyncTask<Void, Void, Void>() {

        override fun doInBackground(vararg params: Void): Void? {
            try {
                // Note for wave files, we can just send data from the file right to the server.
                // In the case you are not an audio file in wave format, and instead you have just
                // raw data (for example audio coming over bluetooth), then before sending up any
                // audio data, you must first send up an SpeechAudioFormat descriptor to describe
                // the layout and format of your raw audio data via DataRecognitionClient's sendAudioFormat() method.
                // String filename = recoMode == SpeechRecognitionMode.ShortPhrase ? "whatstheweatherlike.wav" : "batman.wav";
                val fileStream = assets.open(filename)
                var bytesRead = 0
                val buffer = ByteArray(1024)

                do {
                    // Get  Audio data to send into byte buffer.
                    bytesRead = fileStream.read(buffer)

                    if (bytesRead > -1) {
                        // Send of audio data to service.
                        dataClient.sendAudio(buffer, bytesRead)
                    }
                } while (bytesRead > 0)

            } catch (throwable: Throwable) {
                throwable.printStackTrace()
            } finally {
                dataClient.endAudio()
            }

            return null
        }
    }
}
