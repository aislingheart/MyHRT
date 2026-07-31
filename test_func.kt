import com.google.ai.client.generativeai.type.FunctionResponsePart
import org.json.JSONObject
val p = FunctionResponsePart("name", JSONObject(mapOf("a" to "b")))
