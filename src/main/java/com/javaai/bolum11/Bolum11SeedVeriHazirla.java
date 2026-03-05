package com.javaai.bolum11;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class Bolum11SeedVeriHazirla {

    private static final String VECTOR_TABLE_NAME = "vector_store_b9"; // pgvector tablosu — B9'dan gelen kalici tablo adi
    private static final List<String> ALL_LESSONS = List.of("b111", "b112", "b113", "b114", "b115", "b116");
    private static final Map<String, String> LESSON_LABELS = Map.of( // Okunabilir etiketler
        "b111", "B11.1", "b112", "B11.2", "b113", "B11.3",
        "b114", "B11.4", "b115", "B11.5", "b116", "B11.6"
    );

    private final VectorStore vectorStore; // Embedding + depolama — add() ile chunk yazilir
    private final JdbcTemplate jdbcTemplate;

    public Bolum11SeedVeriHazirla(VectorStore vectorStore, JdbcTemplate jdbcTemplate) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
    }

    private static String uuid(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static Document doc(String key, String content, Map<String, Object> metadata) {
        return new Document(uuid(key), content, metadata);
    }

    public Map<String, Object> seedAll() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String lesson : ALL_LESSONS) {
            result.put(lesson, seedLesson(lesson));
        }
        return result;
    }

    public Map<String, Object> seedLesson(String lessonCode) {
        List<Document> documents = getDocuments(lessonCode);
        if (documents.isEmpty()) {
            throw new IllegalArgumentException("Bilinmeyen lesson: " + lessonCode);
        }

        this.jdbcTemplate.update( // Ham SQL ile metadata filtreli silme — VectorStore.delete() bu filtre seviyesini desteklemiyor
            String.format("DELETE FROM %s WHERE metadata->>'lesson' = ?", VECTOR_TABLE_NAME),
            lessonCode
        );
        this.vectorStore.add(documents); // Her Document icin embedding otomatik uretilir ve pgvector tablosuna yazilir

        return Map.of(
            "lesson", LESSON_LABELS.getOrDefault(lessonCode, lessonCode), // Okunabilir etiket (B11.1 vb.)
            "indexedCount", documents.size(), // Kac dokuman eklendi
            "lessonFilter", lessonCode, // Hangi metadata degeriyle filtrelendi
            "notes", "Seed tamamlandi." // Basari mesaji
        );
    }

    private List<Document> getDocuments(String lessonCode) {
        return switch (lessonCode) {
            case "b111" -> b111Docs();
            case "b112" -> b112Docs();
            case "b113" -> b113Docs();
            case "b114" -> b114Docs();
            case "b115" -> b115Docs();
            case "b116" -> b116Docs();
            default -> List.of();
        };
    }

    private List<Document> b111Docs() {
        String l = "b111";
        return List.of(
            doc("b111-chat-client",
                "ChatClient, Spring AI'da LLM'lerle iletisim kurmanin temel API'sidir ve istemci tarafli tum prompt "
                    + "yonetimini ustlenir. Builder pattern ile olusturulur; prompt(), user(), call(), content() zinciri "
                    + "ile senkron metin yaniti alinir. ChatClient nedir sorusunun cevabi kisaca budur: LLM'e soru "
                    + "gonderip cevap alan istemci. defaultSystem() ile her istekte tekrarlanan sistem yonergesi "
                    + "tanimlanabilir, defaultAdvisors() ile RAG veya bellek gibi ek yetenekler eklenebilir. Neden "
                    + "onemlidir? Cunku Spring AI ekosistemindeki tum model etkilesimlerinin merkezi giris noktasidir. "
                    + "clone() ile endpoint bazli izole kopyalar olusturularak thread-safety garanti edilir.",
                Map.of("lesson", l, "topic", "chat-client", "source", "b111-seed")),
            doc("b111-rag",
                "RAG (Retrieval-Augmented Generation), LLM'in kendi parametrik bilgisi yerine harici kaynaklardan "
                    + "alinan guncel veriye dayanarak yanit uretmesini saglayan bir mimaridir. RAG nedir sorusunun "
                    + "cevabi: retrieval, augmentation ve generation adimlarindan olusan bir pipeline. Bu yaklasim "
                    + "hallucination riskini onemli olcude azaltir cunku model yalnizca saglanan baglamdan cevap uretir. "
                    + "RAG neden kullanilir? Model egitim verisi disindaki guncel veya gizli bilgilere erisim icin "
                    + "vazgecilmezdir. RAG ile LLM daha guvenilir ve dogrulanabilir yanitlar sunar; her iddia "
                    + "VectorStore'daki kaynak belgeye kadar izlenebilir. Retrieval adiminda benzerlik skoru ile "
                    + "ilgisiz belgeler elenebilir, boylece baglam kalitesi arttirilir.",
                Map.of("lesson", l, "topic", "rag", "source", "b111-seed")),
            doc("b111-vector-store",
                "VectorStore, metin belgelerini embedding vektorleri olarak depolayan ve kosinus benzerligi ile "
                    + "arama yapan Spring AI arayuzudur. VectorStore nasil calisir sorusunun cevabi: add() ile belge "
                    + "eklerken embedding otomatik uretilir, similaritySearch() ile sorgu vektoru depolanan belgelerle "
                    + "karsilastirilir. PgVectorStore, SimpleVectorStore ve ChromaVectorStore en yaygin "
                    + "implementasyonlardir. topK ve similarityThreshold parametreleri arama sonuclarini kontrol eder. "
                    + "VectorStore neden tercih edilir? Anlamsal arama yapabilmesi, klasik SQL LIKE sorgularinin "
                    + "esleyemedigi esanlamli sorulari bile dogru belgeye yonlendirmesini saglar. delete() metodu ile "
                    + "lesson bazli idempotent seed operasyonlari guvle uygulanabilir.",
                Map.of("lesson", l, "topic", "vector-store", "source", "b111-seed")),
            doc("b111-embedding",
                "Embedding, dogal dil metnini sabit boyutlu sayisal vektore donusturerek anlamsal benzerliklerin "
                    + "matematiksel olarak hesaplanabilir hale gelmesini saglar. Embedding nedir sorusunun cevabi: her "
                    + "kelime ve cumleyi yuksek boyutlu uzayda temsil eden sayisal donusum. Neden kullanilir? Cunku "
                    + "bilgisayar metni anlayamaz; embedding bu anlami sayilara cevirir. OpenAI text-embedding-ada-002 "
                    + "gibi modeller 1536 boyutlu vektor uretir. Kosinus benzerligi bu vektorler arasindaki aciyi "
                    + "olcerek 0-1 arasi skor hesaplar; 1'e yakin degerler yuksek benzerlik gosterir. Embedding "
                    + "boyutu ne anlama gelir? Buyuk boyut zengin anlam temsili saglar ancak depolama ve hesaplama "
                    + "maliyeti de buna paralel olarak artar. RAG kalitesi buyuk olcude embedding kalitesiyle orantilidir.",
                Map.of("lesson", l, "topic", "embedding", "source", "b111-seed")),
            doc("b111-advisor",
                "QuestionAnswerAdvisor, Spring AI'nin hazir RAG advisor sinifidir ve retrieval ile augmentation "
                    + "adimlarini otomatik yonetir. Advisor ne yapar sorusunun cevabi: VectorStore'dan belge cekmek "
                    + "ve prompt'a baglam olarak eklemek. Builder ile topK, similarityThreshold ve filterExpression "
                    + "parametreleri tanimlanir. ChatClient.defaultAdvisors() ile baglanan advisor her prompt "
                    + "cagirisinda otomatik retrieval yapar ve bulunan belgeleri LLM baglamina enjekte eder. Advisor "
                    + "neden kullanilir? Retrieval mantigini her endpoint'te tekrar yazmak yerine tek yerde tanimlamak "
                    + "DRY prensibini korur. filterExpression ile runtime'da lesson bazli metadata filtresi uygulanarak "
                    + "arama yalnizca ilgili belgelerle sinirlandirilabilir.",
                Map.of("lesson", l, "topic", "advisor", "source", "b111-seed")),
            doc("b111-corrective-rag",
                "Corrective RAG (CRAG), retrieval adiminda getirilen her belgenin kalitesini LLM ile puanlayarak "
                    + "dusuk ilgili dokumanlari baglam disinda birakan gelismis bir RAG desenidir. CRAG nedir sorusunun "
                    + "cevabi: retrieval sonrasi kalite kontrol mekanizmasi. Her belge icin 0-1 arasi ilgi puani "
                    + "hesaplanir ve esik altindakiler elenir. CRAG neden gereklidir? Normal RAG kor retrieval yapar; "
                    + "vektore benzeyen ama konuyla alakasiz belgeler LLM'i yaniltabilir. Hic belge gecemezse web "
                    + "fallback tetiklenir — boylece LLM asla kalitesiz baglamla yanit uretmez. Corrective RAG, "
                    + "Normal RAG'e gore daha yuksek precision saglar ve hallucination riskini belirgin bicimde "
                    + "azaltir. Bu desen ozellikle kritik bilgi dogrulugu gerektiren uygulamalar icin idealdir.",
                Map.of("lesson", l, "topic", "corrective-rag", "source", "b111-seed"))
        );
    }

    private List<Document> b112Docs() {
        String l = "b112";
        return List.of(
            doc("b112-embedding",
                "EmbeddingModel, Spring AI'da metni sabit boyutlu sayisal vektore donusturen ana arayuzdur ve "
                    + "RAG pipeline'inin anlam temelli retrieval yeteneginin kaynagindir. Embedding modeli nasil "
                    + "calisir sorusunun cevabi: metin girdi olarak verilir, model bunu yuksek boyutlu uzayda bir "
                    + "nokta olarak temsil eder. embed() metodu tek metin, embedAll() ise toplu donusum saglar. "
                    + "Benzerlik aramasi bu vektorler uzerinden kosinus mesafesi hesaplayarak en yakin komsulari bulur. "
                    + "Neden bu model secilir? Embedding kalitesi retrieval basarisini dogrudan etkiler; buyuk boyutlu "
                    + "modeller zengin anlam temsili saglarken daha fazla depolama alanine ihtiyac duyar. Ollama ile "
                    + "yerel modeller kullanildiginda gizlilik avantaji da elde edilir.",
                Map.of("lesson", l, "topic", "embedding", "source", "b112-seed")),
            doc("b112-etl",
                "ETL Pipeline, belge islemede uc temel adimi birlestiren bir veri akis mimarisidir: DocumentReader "
                    + "belgeyi okur, DocumentTransformer parcalar ve zenginlestirir, DocumentWriter VectorStore'a yazar. "
                    + "ETL pipeline nasil calisir sorusunun cevabi: oku, donustur, yaz adimlarinin sirasiyla "
                    + "isletilmesi. Spring AI'da PDF, Markdown ve web sayfalari icin hazir reader implementasyonlari "
                    + "bulunur. TokenTextSplitter en yaygin transformer olup uzun belgeleri chunk'lara boler. ETL "
                    + "neden onemlidir? Ham belgenin direkt VectorStore'a eklenmesi yerine parcalanmis ve metadata "
                    + "zenginlestirilmis veri, retrieval kalitesini belirgin sekilde arttirir. Chunk boyutu ve overlap "
                    + "degerlerinin dogru ayarlanmasi RAG basarisinin temel belirleyicilerinden biridir.",
                Map.of("lesson", l, "topic", "etl", "source", "b112-seed")),
            doc("b112-streaming",
                "Spring AI'da stream() metodu Server-Sent Events (SSE) ile parcali yanit saglarken, call() metodu "
                    + "senkron tek seferlik cevap dondurur. Streaming nedir sorusunun cevabi: LLM yanitini beklemeden "
                    + "parcalar halinde alan iletisim yontemi. stream() kullanici deneyimini iyilestirir cunku ilk "
                    + "token aninda ekranda gorulur ve kullanici bekleme surecinde kaldigindan emin olur. call() ise "
                    + "tum yanitin tamamlanmasini bekler; batch islemler, API entegrasyonlari ve otomatik pipeline'lar "
                    + "icin idealdir. Streaming ne zaman tercih edilir? Uzun yanitlar uretilen sohbet uygulamalarinda "
                    + "stream() vazgecilmezdir. Kisa ve kesin bir cevap gereken sorgu-yanit sistemlerinde call() "
                    + "daha pratik ve test edilebilir bir yaklasim sunar.",
                Map.of("lesson", l, "topic", "streaming", "source", "b112-seed")),
            doc("b112-memory",
                "MessageWindowChatMemory, sohbet gecmisinden son N mesaji tutan bellek yoneticisidir ve pencere "
                    + "disindaki mesajlar kontekstten duser. Chat memory nasil calisir sorusunun cevabi: her turda "
                    + "eklenen mesajlar saklanir, limit asildiginda en eski mesajlar kayar ve silinir. Bu yaklasim "
                    + "token tuketimini kontrol altinda tutar cunku sinirsiz gecmis LLM'in context window'unu asabilir. "
                    + "Chat bellek neden gereklidir? LLM her istegi bagimsiz islediginden, onceki konusma baglami "
                    + "aktarilmazsa kullanicinin sorusuna alakasiz yanitlar uretilir. ChatMemoryRepository ile mesajlar "
                    + "veritabaninda kalici olarak saklanabilir; boylece uygulama yeniden baslatildiktan sonra da "
                    + "konusma gecmisi korunur.",
                Map.of("lesson", l, "topic", "memory", "source", "b112-seed")),
            doc("b112-evaluator",
                "RelevancyEvaluator, uretilen RAG cevabinin kullanici sorusuyla ne kadar ilgili oldugunu LLM "
                    + "yargilamasiyla puanlayan bir degerlendirme aracidir. Evaluator ne yapar sorusunun cevabi: "
                    + "cevap kalitesini otomatik olcen ve raporlayan mekanizma. FactCheckingEvaluator ise cevabin "
                    + "saglanan baglamdaki bilgilerle tutarli olup olmadigini kontrol eder. Evaluator neden onemlidir? "
                    + "Insan gozleminin milyonlarca istek icin surdurulemez oldugu uretim ortamlarinda otomatik "
                    + "kalite kontrolu zorunludur. Bu degerlendirme araclari RAG pipeline'inin guvenilir calisip "
                    + "calismadigini izlemek icin kritik oneme sahiptir. LLM-as-a-Judge yaklasimi ile her yanit icin "
                    + "skor uretilir ve esik altindaki cevaplar uyarilari tetikler.",
                Map.of("lesson", l, "topic", "evaluator", "source", "b112-seed")),
            doc("b112-vector-store",
                "VectorStore arayuzu, Spring AI'da embedding vektorlerini depolayan ve benzerlik aramasi yapan "
                    + "tum implementasyonlarin ortak sozlesmesidir. VectorStore ne yapar sorusunun cevabi: add() ile "
                    + "belge ekleme, delete() ile silme ve similaritySearch() ile arama islemlerini standartlastirir. "
                    + "PgVectorStore PostgreSQL tabanliyken ChromaVectorStore ayri bir sunucu gerektirir. Bu soyutlama "
                    + "sayesinde depolama teknolojisi degisse bile uygulama kodu degismez. VectorStore secimi neden "
                    + "onemlidir? Uretim ortaminda performans, kalicilik ve olceklenebilirlik gereksinimleri dogru "
                    + "implementasyonu secmeyi kritik kilar. SimpleVectorStore prototipleme, PgVectorStore kalici "
                    + "uretim deployment'lari icin tercih edilmektedir.",
                Map.of("lesson", l, "topic", "vector-store", "source", "b112-seed"))
        );
    }

    private List<Document> b113Docs() {
        String l = "b113";
        return List.of(
            doc("b113-chat-client",
                "ChatClient, Spring AI'da LLM'e soru gonderip cevap almanin ana arayuzudur ve Builder pattern ile "
                    + "yapilandirilir. ChatClient nasil kullanilir sorusunun cevabi: builder.defaultSystem() ile sistem "
                    + "tonu ayarlanir, build() ile immutable instance olusturulur. prompt().user(q).call().content() "
                    + "zinciri senkron metin yaniti saglar. ChatClient neden Builder ile kurulur? Cunku advisor, model "
                    + "secenegi ve sistem prompt gibi pek cok yapilandirma secenek bir arada yonetilebilir olmalidir. "
                    + "clone() ile endpoint bazli izole kopyalar olusturulabilir; her kopya farkli advisor veya "
                    + "ayarlarla calisarak RAG ile bellek gibi yetenekleri bagimsiz bicimde barindirabilir.",
                Map.of("lesson", l, "topic", "chat-client", "source", "b113-seed")),
            doc("b113-advisor",
                "QuestionAnswerAdvisor, kullanici sorusuna gore VectorStore'dan ilgili dokumanlari otomatik ceken "
                    + "ve prompt'a baglam olarak ekleyen hazir bir RAG advisor sinifidir. Advisor nasil calisir "
                    + "sorusunun cevabi: her prompt cagirisinda retrieval ve augmentation adimlarini arka planda "
                    + "yurutur, gelistirici bu detaylarla ugrasarak zaman kaybetmez. SearchRequest ile topK ve "
                    + "threshold tanimlanir; FILTER_EXPRESSION ile runtime'da metadata filtresi uygulanarak arama "
                    + "sadece ilgili belge grubuna sinirlandirilir. Advisor neden tercih edilir? Hazir implementasyon "
                    + "oldugundan basit RAG senaryolarinda QuestionAnswerAdvisor tek basina yeterlidir ve elle "
                    + "retrieval kodu yazmak zorunda kalinmaz. Karmasik senaryolar icin RetrievalAugmentationAdvisor "
                    + "daha esnek bir alternatidir.",
                Map.of("lesson", l, "topic", "advisor", "source", "b113-seed")),
            doc("b113-modular-rag",
                "RetrievalAugmentationAdvisor, modular RAG pipeline'inin orkestrasyon katmanidir ve retriever, "
                    + "augmenter, transformer gibi bilesenleri bagimsiz yapilandirma imkani tanir. Modular RAG nedir "
                    + "sorusunun cevabi: her RAG adiminin ayri bir nesne olarak tanimlandigi, degistirilebilir ve "
                    + "test edilebilir esnek mimari. Neden modular? Monolitik RAG kodunda tek bir degisiklik tum "
                    + "pipeline'i etkileyebilir; modular mimari her bilesenin bagimsiz gelistirilip test edilmesini "
                    + "saglar. documentRetriever() ile retrieval, queryTransformers() ile pre-retrieval donusumu, "
                    + "documentPostProcessors() ile post-retrieval filtreleme bagimsiz yonetilir. Bu mimari farkli "
                    + "retrieval stratejilerini (hibrit arama, reranking) kolayca degistirip denemeye olanak tanir.",
                Map.of("lesson", l, "topic", "modular-rag", "source", "b113-seed")),
            doc("b113-crag",
                "Corrective RAG (CRAG), retrieval sonrasi her belgenin ilgi kalitesini LLM ile puanlayarak dusuk "
                    + "skorlu dokumanlari eleyip yalnizca kaliteli baglamla cevap ureten gelismis bir RAG desenidir. "
                    + "CRAG nasil calisir sorusunun cevabi: her aday belge icin 0-1 arasi kalite puani hesaplanir, "
                    + "esik altindakiler dislanir ve yalnizca guclu adaylar LLM baglamina aktarilir. Hic belge "
                    + "gecemezse fallback mekanizmasi devreye girer ve alternatif kaynaklara basvurulur. CRAG neden "
                    + "Normal RAG'dan ustundur? Normal RAG kor retrieval yapar; CRAG retrieval kalitesini olcarak "
                    + "gereksiz veya yaniltici belgelerin LLM'e ulasmasini engeller. Bu yaklasim hallucination "
                    + "riskini azaltir ve precision degerini belirgin sekilde iyilestirir.",
                Map.of("lesson", l, "topic", "crag", "source", "b113-seed")),
            doc("b113-adaptive-rag",
                "Adaptive RAG, her sorunun karmasikligini LLM ile siniflandirarak uygun RAG stratejisine "
                    + "yonlendiren akilli bir yonlendirme desenidir. Adaptive RAG nedir sorusunun cevabi: sorgu "
                    + "tipleme ve strateji secim mekanizmasi. SIMPLE sorgular dogrudan LLM ile cevaplanir, MODERATE "
                    + "sorgular standart RAG kullanir, COMPLEX sorgular CRAG benzeri kalite filtreli pipeline'dan "
                    + "gecer. Adaptive RAG neden kullanilir? Her sorguya ayni kaynagi harcamak verimsizdir; basit "
                    + "sorulara VectorStore aramasi yapip maliyet olusturmak gereksizdir. Bu triaj sistemi kaynaklari "
                    + "verimli dagitarak hem maliyet hem gecikme optimize edilir. Siniflandirma adimi ekstra bir LLM "
                    + "cagrisina mal olsa da kazanilan verimlilik bunu fazlasiyla karsilar.",
                Map.of("lesson", l, "topic", "adaptive-rag", "source", "b113-seed")),
            doc("b113-retrieval-params",
                "VectorStore benzerlik esigi (similarityThreshold) ve topK parametreleri retrieval kalitesini "
                    + "dogrudan etkileyen iki temel ayardir. Retrieval parametreleri nasil ayarlanir sorusunun cevabi: "
                    + "topK aday sayisini, threshold ise minimum benzerlik skorunu belirler. Yuksek topK genis baglam "
                    + "saglar ama gurultu riski artar; yuksek threshold kesin eslesmeler getirir ama kapsam daralir. "
                    + "Bu iki parametre neden birlikte degerlendirilmeli? Birinin degerini artirmak digerinin "
                    + "etkisini dengeleyebilir; ornegin dusuk threshold ile genis kapsam alinirken yuksek topK ile "
                    + "fazla aday islenmesi maliyeti arttirir. Sweep testi ile farkli kombinasyonlar olculup optimal "
                    + "deger veriye dayali olarak sistematik sekilde belirlenebilir.",
                Map.of("lesson", l, "topic", "retrieval-params", "source", "b113-seed"))
        );
    }

    private List<Document> b114Docs() {
        String l = "b114";
        return List.of(
            doc("b114-spring-ai",
                "Spring AI, Java uygulamalarinda buyuk dil modelleri (LLM) ile entegrasyon kurmayi kolaylastiran "
                    + "bir framework'tur. Spring AI nedir sorusunun cevabi: ChatClient, PromptTemplate, Advisor "
                    + "mimarisi ve Tool Calling gibi bilesenleri iceren kapsamli bir AI gelistirme platformu. OpenAI, "
                    + "Anthropic ve Ollama gibi model saglayicilari destekler. Spring AI neden tercih edilir? Spring "
                    + "Boot ekosistemiyle butunlesik calistigindan mevcut Java projelerine ekstra konfigürasyon "
                    + "gerektirmeksizin entegre edilebilir. RAG, embedding ve structured output gibi temel AI "
                    + "yeteneklerini hazir bilesenlerle sunarken, provider bagimsizligi sayesinde OpenAI'dan "
                    + "Ollama'ya kisa surede gecis yapmak mumkundur.",
                Map.of("lesson", l, "topic", "spring-ai", "source", "b114-seed")),
            doc("b114-vector-store",
                "VectorStore, embedding tabanli benzerlik aramasi yapan Spring AI'nin veri depolama arayuzudur. "
                    + "VectorStore turleri nelerdir sorusunun cevabi: PgVectorStore PostgreSQL pgvector uzantisi ile "
                    + "kalici depolama, ChromaVectorStore ayri sunucu bazli hafif depolama ve SimpleVectorStore JVM "
                    + "bellekte gecici depolama saglar. add() ile belge eklenir, similaritySearch() ile sorgu vektoru "
                    + "depolanan vektorlerle karsilastirilarak en benzer belgeler skor sirasiyla doner. Hangi "
                    + "VectorStore secilmeli? Prototiplerde SimpleVectorStore pratiktir, ancak restart edilince "
                    + "veri kaybolur. Uretim ortaminda PgVectorStore HNSW indeksiyle milyonlarca vektor arasinda "
                    + "milisaniye mertebesinde arama yapabilmesiyle one cikip tercih sebebi olur.",
                Map.of("lesson", l, "topic", "vector-store", "source", "b114-seed")),
            doc("b114-rag",
                "RAG (Retrieval-Augmented Generation), LLM'e harici bilgi kaynagi ekleyerek hallucination riskini "
                    + "azaltan ve guncel veriyle cevap uretilmesini saglayan bir mimari desendir. RAG nasil calisir "
                    + "sorusunun cevabi: retrieval adiminda VectorStore'dan ilgili belgeler cekilir, augmentation "
                    + "adiminda bunlar prompt'a eklenir, generation adiminda LLM baglamli yanit uretir. RAG neden "
                    + "hallucination azaltir? Cunku model serbest uretim yapmak yerine saglanan somut belgelerden "
                    + "yanit olusturmak zorunda kalir. Bu uc adimli pipeline, modelin kendi egitim bilgisi disindaki "
                    + "sorulara bile dogru yanit vermesini saglar. Retrieval adiminin kalitesi nihai cevap kalitesini "
                    + "dogrudan belirler; bu nedenle Corrective ve Adaptive RAG gibi ileri desenler ortaya cikmistir.",
                Map.of("lesson", l, "topic", "rag", "source", "b114-seed")),
            doc("b114-embedding",
                "Embedding, metni sayisal vektore donusturerek anlamsal benzerliklerin hesaplanmasini saglayan "
                    + "temel bir NLP islemidir. Embedding modeli nedir sorusunun cevabi: her kelime ve cumleyi yuksek "
                    + "boyutlu uzayda bir nokta olarak temsil eden matematiksel donusum. OpenAI text-embedding-ada-002 "
                    + "1536, nomic-embed-text 768 boyutlu vektor uretir. Vektor boyutu arttikca anlam temsili "
                    + "zenginlesir ancak depolama ve arama maliyeti de buna paralel artar. Embedding modeli nasil "
                    + "secilir? Gizlilik oncelikliyse Ollama ile yerel model kullanilir; yuksek kalite oncelikliyse "
                    + "OpenAI veya Cohere gibi bulut tabanli modeller tercih edilir. Indexed belge sayisi ve sorgu "
                    + "hizi da secimi etkileyen diger onemli kriterlerdir.",
                Map.of("lesson", l, "topic", "embedding", "source", "b114-seed")),
            doc("b114-tool-calling",
                "Tool Calling, LLM'e disaridan Java fonksiyonlari cagirma yetenegi kazandiran Spring AI "
                    + "mekanizmasidir. Tool Calling nedir sorusunun cevabi: LLM'in ne zaman hangi fonksiyonu "
                    + "cagracagina kendi karar verdigi ozerk fonksiyon cagri sistemi. Spring AI'da @Tool annotation'i "
                    + "ile tanimlanir ve ChatClient.defaultTools() ile LLM'e tanitilir. LLM, kullanici sorusuna gore "
                    + "gerekli tool'u secer, parametreleri JSON olarak belirler ve Java metodu cevap urettikten sonra "
                    + "sonucu yorumlayip kullaniciya dogal dilde iletir. Tool Calling ne zaman kullanilir? Gercek "
                    + "zamanli kur bilgisi, veritabani sorgulari veya dis API erisimi gibi LLM'in kendi bilgisiyle "
                    + "cevaplayamayacagi durumlarda vazgecilmezdir.",
                Map.of("lesson", l, "topic", "tool-calling", "source", "b114-seed")),
            doc("b114-mcp",
                "MCP (Model Context Protocol), LLM ile dis servisler arasinda standart bir iletisim protokolu "
                    + "tanimlayan acik bir spesifikasyondur. MCP nedir sorusunun cevabi: tool, resource ve prompt "
                    + "kavramlarini standartlastiran ve birlikte calisabilirlik saglayan protokol. Spring AI'da "
                    + "McpSyncClient ile entegre edilir ve ToolCallbackProvider uzerinden ChatClient'a baglanir. "
                    + "MCP neden onemlidir? Farkli AI uygulamalari ve servisler ortak bir dil konusarak tool "
                    + "paylasimi gerceklestirebilir; bir MCP server olarak tanimlanan fonksiyon birden fazla AI "
                    + "istemcisi tarafindan kullanilabilir. MCP, @Tool annotation'ina gore daha genis ekosistem "
                    + "uyumu ve dil bagimsizligi avantaji sunar.",
                Map.of("lesson", l, "topic", "mcp", "source", "b114-seed"))
        );
    }

    private List<Document> b115Docs() {
        String l = "b115";
        return List.of(
            doc("b115-rag-optimization",
                "RAG pipeline optimizasyonu, retrieval kalitesi, chunk boyutu ayari ve reranking gibi tekniklerin "
                    + "sistematik uygulanmasidir. RAG nasil optimize edilir sorusunun cevabi: topK ve threshold sweep "
                    + "testi ile kalibrasyon, dokuman boyutunun TokenTextSplitter ile ayarlanmasi ve post-retrieval "
                    + "reranking. topK artirmak baglam zenginlestirir ama maliyet ve gecikme artar; dengeli ayar "
                    + "gerekir. RAG optimizasyonu neden onemlidir? Varsayilan parametreler tum veri setleri icin "
                    + "optimal degildir; uygulama ozeline gore tuning yapilmazsa retrieval kalitesi duserek yanit "
                    + "kalitesini olumsuz etkiler. Parametre tuning, tahmin degil olcum ile yapilmali; gold standard "
                    + "soru seti uzerinden Precision, Recall ve NDCG metrikleri ile degerlendirilmelidir.",
                Map.of("lesson", l, "topic", "rag-optimization", "source", "b115-seed")),
            doc("b115-embedding",
                "Embedding modeli secimi RAG basarisini dogrudan etkiler cunku retrieval kalitesi embedding'in "
                    + "anlam temsil gucune baglidir. Embedding modeli nasil secilir sorusunun cevabi: kullanim "
                    + "senaryosuna gore boyut, hiz ve anlam zenginligi arasinda denge kurulmalidir. OpenAI "
                    + "text-embedding-3-small 1536 boyutlu vektor uretir; buyuk vektor zengin anlam temsili saglar "
                    + "ama depolama maliyeti artar. Ollama nomic-embed-text yerel calisarak gizlilik avantaji sunar "
                    + "ve disariya veri gondermez. Embedding modeli degistirilebilir mi? Evet, ancak mevcut indexin "
                    + "tamamen yeniden olusturulmasi gerekir cunku farkli modeller birbiriyle uyumsuz vektor uzaylari "
                    + "uretir. Bu nedenle model secimi erkenden yapilmali ve production oncesi kararlestirilmelidir.",
                Map.of("lesson", l, "topic", "embedding", "source", "b115-seed")),
            doc("b115-vectorstore",
                "VectorStore ve embedding modeli birlikte calisarak metin belgelerinin vektorel temsilini depolayan "
                    + "ve arayan bir sistem olusturur. VectorStore ve embedding iliskisi nedir sorusunun cevabi: "
                    + "embedding metni vektore cevirir, VectorStore bu vektorleri saklar ve sorgular. pgvector HNSW "
                    + "indeksi milyonlarca vektor icinde bile milisaniye mertebesinde hizli arama saglar. Hangi "
                    + "VectorStore ne zaman kullanilir? SimpleVectorStore prototipler icin pratik ancak kalici degil, "
                    + "PgVectorStore production ve kalicilik gerektiren sistemler icin, ChromaVectorStore orta olcekli "
                    + "ve ayri depolama sunucusu istenen projeler icin tercih edilir. Indexleme stratejisi de "
                    + "performansi onemli olcude etkiler; HNSW ve IVFFlat arasindaki tercih veri buyuklugune gore "
                    + "belirlenir.",
                Map.of("lesson", l, "topic", "vectorstore", "source", "b115-seed")),
            doc("b115-chunking",
                "Chunk boyutu ve overlap degerleri retrieval kalitesini dogrudan etkileyen kritik ETL "
                    + "parametreleridir. Chunking nasil yapilir sorusunun cevabi: TokenTextSplitter ile uzun belgeler "
                    + "token sayisina gore parcalanir, overlap ile ardisik parcalar arasinda ortak metin birakilarak "
                    + "baglam kaybi azaltilir. Kucuk chunk'lar hassas esleme saglar ama baglam kaybeder; buyuk "
                    + "chunk'lar genis baglam tutar ama ilgisiz bilgi icerme riski artar. Chunking neden kritik? "
                    + "Yanlis boyutlandirma retrieval'i dogrudan bozar; anahtar bilgi chunk sinirinda ikiye bolunurse "
                    + "sorguya hic getirilmeyebilir. Overlap bu riski azaltir cunku sinir bolgesindeki metin her iki "
                    + "chunk'a da dahil edilerek icerik parcalanmaz.",
                Map.of("lesson", l, "topic", "chunking", "source", "b115-seed")),
            doc("b115-reranking",
                "Reranking, vektorel aramadan gelen ilk adaylari cross-encoder veya LLM ile yeniden siralayarak "
                    + "en ilgili belgelerin listenin basina tasinmasini saglar. Reranking nedir sorusunun cevabi: "
                    + "ilk retrieval'in kaba siralamasini hassas bir ikinci degerlendirmeyle iyilestirme. "
                    + "DocumentPostProcessor arayuzu ile Spring AI pipeline'ina entegre edilir. Reranking neden "
                    + "gereklidir? Bi-encoder tabanli vektorel arama hizlidir ama tam anlamsal uyumu hic zaman "
                    + "yakalayamayabilir; cross-encoder sorgu-belge ciftini birlikte degerlendirerek daha isabetli "
                    + "skor uretir. Wide retrieval, narrow output stratejisi: genis topK ile cok aday al, reranker "
                    + "ile en iyileri sec; bu kombine yaklasim hem hiz hem kaliteyi optimize eder.",
                Map.of("lesson", l, "topic", "reranking", "source", "b115-seed")),
            doc("b115-hybrid-search",
                "Hibrit arama, vektor benzerligi ile BM25 keyword aramasini birlestirerek hem anlam hem de tam "
                    + "kelime eslesmesi basarisini arttirir. Hibrit arama nedir sorusunun cevabi: iki farkli arama "
                    + "yonteminin Reciprocal Rank Fusion (RRF) ile birlestirilmesi. Teknik terimlerde keyword arama "
                    + "embedding'e gore daha isabetli calisir cunku rare token'lar vektor uzayinda kaybolabilir. "
                    + "Anlamsal sorgularda ise embedding ustundur; esanlamli ifadeler birebir kelime eslesme "
                    + "olmaksizin dogru belgeye ulastirir. Hibrit arama ne zaman tercih edilir? Alan adlarini iceren "
                    + "teknik dokumanlarda kesinlikle onerilir; RRF ile her iki listenin skorlari birlestirilip "
                    + "ikisinin guclu yanlarindan yararlanilarak genel retrieval kalitesi arttirilir.",
                Map.of("lesson", l, "topic", "hybrid-search", "source", "b115-seed"))
        );
    }

    private List<Document> b116Docs() {
        String l = "b116";
        return List.of(
            doc("b116-rag-tanim",
                "RAG (Retrieval-Augmented Generation), vektore dayali belge getirerek LLM'in hallucination riskini "
                    + "azaltan bir mimari yaklasimdir. RAG ne yapar sorusunun cevabi: retrieval, augmentation ve "
                    + "generation adimlarindan olusan pipeline ile kaynaga dayali yanit uretimi. VectorStore'dan "
                    + "cekilen belgeler LLM baglamina eklenir ve model yalnizca saglanan baglamdan cevap olusturur. "
                    + "RAG olmadan ne olur? Model kendi egitim verisiyle sinirli kalir; guncel veya gizli bilgilere "
                    + "erisemez ve tahmin ederek yanit uretir, bu da hallucination riskini arttirir. RAG sayesinde "
                    + "her iddia dogrulanabilir hale gelir ve kaynak belgeye kadar izlenebilir bir yanit uretilir.",
                Map.of("lesson", l, "topic", "rag-tanim", "source", "b116-seed")),
            doc("b116-vector-store",
                "VectorStore, embedding tabanli benzerlik aramasi yaparak kullanici sorusuna en yakin belgeleri "
                    + "donduren Spring AI depolama arayuzudur. VectorStore ne yapar sorusunun cevabi: add() ile belge "
                    + "ekler, embedding otomatik uretilir; similaritySearch() ile sorgu vektoru depolanan vektorlerle "
                    + "karsilastirilir ve en benzer belgeler skor sirasiyla doner. VectorStore nasil tercih edilmeli? "
                    + "PgVectorStore production ortamlarinda HNSW indeksiyle milisaniye duzeyinde hizli arama sunar, "
                    + "ChromaVectorStore kolayca ayaga kaldirilan bagimsiz servis olarak orta olcekli projeler icin "
                    + "tercih edilir, SimpleVectorStore ise kalici olmayan prototip ve demo ortamlari icin uygundur. "
                    + "Uygulama kodu VectorStore arayuzunu kullandigi icin gerektiginde implementasyon degistirilebilir.",
                Map.of("lesson", l, "topic", "vector-store", "source", "b116-seed")),
            doc("b116-advisor",
                "QuestionAnswerAdvisor, kullanici sorusunu embed edip VectorStore'dan belgeler getirerek prompt'a "
                    + "baglam olarak ekleyen Spring AI RAG advisor sinifidir. QuestionAnswerAdvisor ne yapar sorusunun "
                    + "cevabi: retrieval ve augmentation adimlarini tek bir abstraction altinda otomatik yonetir, "
                    + "gelistirici bu detaylarla ugrasarak zaman kaybetmez. topK ve similarityThreshold ile arama "
                    + "parametreleri; filterExpression ile metadata bazli lesson filtresi tanimlanir. Advisor neden "
                    + "bu kadar pratik? ChatClient.defaultAdvisors() ile bir kez baglanan advisor, sonraki her "
                    + "prompt cagirisinda otomatik retrieval yapar. Karmasik pipeline icin "
                    + "RetrievalAugmentationAdvisor ile bilesenler bagimsiz yapilandirilabildigi icin daha esnek "
                    + "bir alternatif olarak tercih edilir.",
                Map.of("lesson", l, "topic", "advisor", "source", "b116-seed")),
            doc("b116-chat-client",
                "ChatClient.Builder, Spring AI'da akici prompt olusturma ve model cagrisi icin kullanilan API'dir. "
                    + "ChatClient nasil kullanilir sorusunun cevabi: builder pattern ile defaultSystem(), "
                    + "defaultAdvisors() ve defaultOptions() ayarlanir, build() ile immutable ve thread-safe instance "
                    + "olusturulur. prompt().user(q).call().content() zinciri senkron yanit saglar. ChatClient neden "
                    + "Builder ile olusturulur? Builder pattern zorunlu ve opsiyonel parametreleri net sekilde "
                    + "ayirarak okunabilirligi arttirir ve hatalari derleme zamaninda yakalar. clone() ile endpoint "
                    + "bazli izole kopyalar olusturularak farkli advisor veya sistem promptlari bagimsizca "
                    + "yapilandirilabildigi icin thread-safety garanti edilir.",
                Map.of("lesson", l, "topic", "chat-client", "source", "b116-seed")),
            doc("b116-topk",
                "topK parametresi, vektorel arama sonucunda kac aday belgenin geri donecegini sinirlar ve "
                    + "retrieval kapsamini dogrudan kontrol eder. topK nedir sorusunun cevabi: dusuk topK dar ve "
                    + "odakli baglam, yuksek topK genis ama potansiyel gurultulu baglam olusturur. topK nasil "
                    + "ayarlanmali? Veri seti yapisi, chunk boyutu ve soru tiplerine gore farkli degerler denenip "
                    + "olculmelidir; tek bir ideal deger yoktur. Yuksek topK tutunca context maliyeti artar, token "
                    + "tuketimi yukselir ve ilgisiz belgeler LLM'in dikkatini dagitabilir. Dusuk topK ise ilgili "
                    + "belgelerin kacirilma riskini arttirir. Reranking ile birlestirildiginde genis topK daha "
                    + "guvenli hale gelir cunku reranker gereksiz adaylari eleyebilir.",
                Map.of("lesson", l, "topic", "topk", "source", "b116-seed")),
            doc("b116-threshold",
                "similarityThreshold, belirli bir benzerlik skorunun altindaki belgeleri eleyerek retrieval "
                    + "kalitesini arttirir. Threshold nedir sorusunun cevabi: kosinus benzerlik skorunun alt sinirini "
                    + "belirleyen filtre parametresi. Yuksek threshold yalnizca cok benzer belgeleri gecirir, "
                    + "precision artar ama recall duser; az aday LLM'e aktarilir. Dusuk threshold genis kapsam "
                    + "saglar, recall yukselir ama gurultulu belgeler devreye girebilir. Threshold neden dikkatli "
                    + "secilmeli? Sifir yapildiginda esik devre disi kalir ve tum adaylar doner, bu da LLM'in "
                    + "alakasiz baglamla yanit uretmesine yol acabilir. topK ile birlikte sweep testi yapilarak "
                    + "her iki parametrenin optimal kombinasyonu veri odakli sekilde belirlenmeli.",
                Map.of("lesson", l, "topic", "threshold", "source", "b116-seed"))
        );
    }

}
