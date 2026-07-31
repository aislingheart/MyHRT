import com.google.ai.client.generativeai.type.FunctionDeclaration
import com.google.ai.client.generativeai.type.Schema
import com.google.ai.client.generativeai.type.FunctionDeclaration
fun main() {
    val f = FunctionDeclaration::class.java
    println("FunctionDeclaration constructors:")
    f.constructors.forEach { println(it) }
    
    val s = Schema::class.java
    println("Schema constructors:")
    s.constructors.forEach { println(it) }
}
