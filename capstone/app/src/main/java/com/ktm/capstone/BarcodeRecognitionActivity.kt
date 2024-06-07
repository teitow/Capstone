import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class BarcodeRecognitionActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTTSInitialized = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.KOREAN)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "Korean language is not supported.")
            } else {
                isTTSInitialized = true
                speak("바코드를 스캔해주세요.", "ID_INITIAL")
            }
        } else {
            Log.e("TTS", "Initialization failed.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_barcode_recognition)

        tts = TextToSpeech(this, this)

        // 바코드 스캔 버튼 클릭 리스너 추가
        findViewById<Button>(R.id.scanBarcodeButton).setOnClickListener {
            startBarcodeScanner()
        }
    }

    private fun startBarcodeScanner() {
        val integrator = IntentIntegrator(this)
        integrator.setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES)
        integrator.setPrompt("바코드를 스캔해주세요")
        integrator.setCameraId(0)
        integrator.setBeepEnabled(false)
        integrator.setBarcodeImageEnabled(true)
        integrator.initiateScan()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result: IntentResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            if (result.contents == null) {
                Toast.makeText(this, "스캔 실패", Toast.LENGTH_SHORT).show()
            } else {
                // 스캔 성공
                val barcode = result.contents
                fetchProductInfo(barcode)
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun fetchProductInfo(barcode: String) {
        val apiKey = BuildConfig.BARCODE_API_KEY
        val apiUrl = "https://api.data.go.kr/openapi/barcodeinfo?serviceKey=$apiKey&barcode=$barcode"

        val client = OkHttpClient()
        val request = Request.Builder()
            .url(apiUrl)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@BarcodeRecognitionActivity, "제품 정보를 가져오는데 실패했습니다.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    val productInfo = parseProductInfo(responseBody)
                    runOnUiThread {
                        displayProductInfo(productInfo)
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@BarcodeRecognitionActivity, "제품 정보를 가져오는데 실패했습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun parseProductInfo(responseBody: String?): String {
        responseBody?.let {
            val json = JSONObject(it)
            val productName = json.getString("productName")
            val manufacturerName = json.getString("manufacturerName")
            val origin = json.getString("origin")
            val price = json.getString("price")
            return "제품명: $productName\n제조사: $manufacturerName\n원산지: $origin\n가격: $price"
        }
        return "제품 정보를 찾을 수 없습니다."
    }

    private fun displayProductInfo(info: String) {
        val textView = findViewById<TextView>(R.id.barcodeTextView)
        textView.text = info
        textView.visibility = View.VISIBLE
        speak(info, "ID_PRODUCT_INFO")
    }

    private fun speak(text: String, utteranceId: String) {
        if (isTTSInitialized) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
