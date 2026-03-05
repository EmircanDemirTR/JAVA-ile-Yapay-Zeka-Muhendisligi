package com.javaai.bolum10;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class Bolum10SeedVeriHazirla {

    // Bolum 9'dan devralınan tablo adi — B10 dersleri bu tabloya yazilir
    private static final String VECTOR_TABLE_NAME = "vector_store_b9";

    // Desteklenen 8 ders kodu — seedAll() bu listeyi itere eder
    private static final List<String> ALL_LESSONS = List.of("b102", "b103", "b104", "b105", "b106", "b107", "b108", "b109");

    // Swagger/API ciktisinda kullaniciya gosterilen okunakli ders etiketleri
    private static final Map<String, String> LESSON_LABELS = Map.of(
            "b102", "B10.2", "b103", "B10.3", "b104", "B10.4", "b105", "B10.5",
            "b106", "B10.6", "b107", "B10.7", "b108", "B10.8", "b109", "B10.9"
    );

    private final VectorStore vectorStore;   // Embedding deposu — add() ile dokuman ekler
    private final JdbcTemplate jdbcTemplate; // Ham SQL ile DELETE islemleri icin

    public Bolum10SeedVeriHazirla(VectorStore vectorStore, JdbcTemplate jdbcTemplate) {
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
        Map<String, Object> result = new LinkedHashMap<>(); // LinkedHashMap: ekleme sirasi korunur
        for (String lesson : ALL_LESSONS) {
            result.put(lesson, seedLesson(lesson)); // Her ders icin DELETE + INSERT — herhangi birinde hata olursa exception firlar
        }
        return result;
    }

    public Map<String, Object> seedLesson(String lessonCode) {
        List<Document> documents = getDocuments(lessonCode);
        if (documents.isEmpty()) {
            throw new IllegalArgumentException("Bilinmeyen lesson: " + lessonCode);
        }

        // Mevcut kayitlari temizle — idempotent: tekrar calisinca duplicate olusturmaz
        this.jdbcTemplate.update(
                String.format("DELETE FROM %s WHERE metadata->>'lesson' = ?", VECTOR_TABLE_NAME),
                lessonCode
        );
        this.vectorStore.add(documents); // Embedding otomatik uretilir ve tabloya yazilir

        return Map.of(
                "lesson", LESSON_LABELS.getOrDefault(lessonCode, lessonCode),
                "indexedCount", documents.size(),
                "lessonFilter", lessonCode,
                "notes", "Seed tamamlandi."
        );
    }

    private List<Document> getDocuments(String lessonCode) {
        return switch (lessonCode) {
            case "b102" -> b102Docs(); // B10.2 — IlkRagDemo: ChatClient, advisor, topK, threshold
            case "b103" -> b103Docs(); // B10.3 — NaiveRagProblemleriDemo: 3 ilgili + 4 noise
            case "b104" -> b104Docs(); // B10.4 — ModularRagDemo: RetrievalAugmentationAdvisor
            case "b105" -> b105Docs(); // B10.5 — SorguDonusturmeDemo: rewrite, multi-query, compression
            case "b106" -> b106Docs(); // B10.6 — ParametreOptimizasyonDemo: source metadata
            case "b107" -> b107Docs(); // B10.7 — HibritAramaDemo: TokenTextSplitter, RRF
            case "b108" -> b108Docs(); // B10.8 — RerankingDemo: 4 ilgili + 1 sports noise
            case "b109" -> b109Docs(); // B10.9 — CokKaynakliRagDemo: pdf/md/txt metadata
            default -> List.of();      // Bilinmeyen kod: seedLesson() IllegalArgumentException firlatir
        };
    }

    private List<Document> b102Docs() {
        String l = "b102";
        return List.of(
            doc("b102-chat-client",
                "ChatClient, Spring AI framework'unda LLM'lerle etkilesim kurmanin ana API'sidir ve istemci tarafli tum prompt yonetimini ustlenir. "
                + "ChatClient nedir sorusunun cevabi: Builder pattern ile olusturulan, defaultSystem(), defaultAdvisors() ve defaultOptions() gibi metotlarla yapilandirilan LLM istemci nesnesi. "
                + "Her endpoint icin clone() ile yeni bir kopya olusturmak thread-safety saglar; boylece ayni ChatClient farki isteklerde guvenle kullanilabilir. "
                + "ChatClient.prompt().user(q).call().content() zinciri ile senkron metin yaniti alinir. "
                + "Streaming senaryolarinda call() yerine stream() ile SSE parcali yanit almak da mumkundur.",
                Map.of("lesson", l, "topic", "chat-client", "source", "b102-seed")),
            doc("b102-advisor",
                "QuestionAnswerAdvisor, Spring AI'nin hazir RAG advisor sinifidir ve retrieval ile augmentation adimlarini otomatik yonetir. "
                + "QuestionAnswerAdvisor ne ise yarar sorusunun kisa cevabi: VectorStore'dan belge cekmek ve bu belgeleri prompt'a eklemek. "
                + "Builder ile VectorStore ve SearchRequest parametreleri tanimlanir; her prompt cagirisinda once vektorel arama yapilir, bulunan belgeler prompt'a enjekte edilir. "
                + "FILTER_EXPRESSION parametresi ile runtime'da metadata filtresi uygulanabilir; ornegin lesson='b102' gibi alan bazli daraltma yapilabilir. "
                + "Basit RAG senaryolarinda tek advisor yeterlidir ancak karmasik pipeline'lar icin RetrievalAugmentationAdvisor tercih edilir.",
                Map.of("lesson", l, "topic", "advisor", "source", "b102-seed")),
            doc("b102-topk",
                "SearchRequest.topK parametresi, vektorel arama sonucunda en fazla kac belgenin geri donecegini sinirlar ve retrieval kapsamini dogrudan etkiler. "
                + "topK nedir sorusunun cevabi: LLM'e gonderilecek baglam belgelerinin maksimum adedi; dusuk topK dar ve odakli, yuksek topK genis ama gurultulu baglam demektir. "
                + "topK degeri threshold ile birlikte calisir: yuksek topK ve dusuk threshold kombinasyonu en genis taramayi saglar. "
                + "Optimum deger veri setine gore degisir ve sweep testi ile belirlenebilir; genel tavsiye baslangicta topK=5 ile denemektir. "
                + "topK arttirildiginda LLM prompt'u buyur, bu da maliyet ve latency artisina dogrudan yansir.",
                Map.of("lesson", l, "topic", "topk", "source", "b102-seed")),
            doc("b102-threshold",
                "similarityThreshold, kosinus benzerlik skorunun alt sinirini belirleyerek dusuk kaliteli eslesmeleri sonuc listesinden cikarir; precision kontrolunun ana aracidir. "
                + "similarityThreshold ne anlama gelir sorusunun cevabi: VectorStore'dan donecek belgelerin minimum benzerlik esigi; bu esigi gecemeyen belgeler baglamdan dislanir. "
                + "0.7 degeri cogu senaryo icin iyi bir baslangic noktasidir; daha yuksek deger precision arttirir ama recall duser. "
                + "Threshold sifir yapildiginda tum adaylar dahil edilir; bu durum reranking stratejisiyle birlikte kasitli olarak tercih edilir ve production'da benchmark ile kalibre edilmelidir.",
                Map.of("lesson", l, "topic", "threshold", "source", "b102-seed")),
            doc("b102-rag",
                "Retrieval-Augmented Generation (RAG), LLM'in kendi parametrik bilgisi yerine harici kaynaklardan alinan gercel veriye dayanarak yanit uretmesini saglar. "
                + "RAG nedir sorusunun cevabi: bilgi alma (retrieval), baglamla zenginlestirme (augmentation) ve dil uretimi (generation) adimlarini birlestiren bir mimari. "
                + "Bu yaklasim hallucination riskini onemli olcude azaltir cunku model yalnizca saglanan baglamdan cevap turetir, hayal etmez. "
                + "Kaynak gosterilebilirlik (citation) sayesinde her iddia dogrulanabilir hale gelir; bu ozellikle finans ve hukuk gibi duzenlemeli alanlarda kritik oneme sahiptir.",
                Map.of("lesson", l, "topic", "rag", "source", "b102-seed")),
            doc("b102-vector-store",
                "VectorStore, metin belgelerini embedding vektorleri olarak depolayan ve kosinus benzerligi ile arama yapan Spring AI arayuzudur; semantik aramanin omurgasidir. "
                + "VectorStore ne ise yarar sorusunun cevabi: metni sayisal vektore cevirip depolar, sorgu geldiginde en anlamsal yakin belgeleri skorlayarak dondurur. "
                + "PgVectorStore, SimpleVectorStore ve ChromaVectorStore gibi implementasyonlari mevcuttur; her biri degistirilebilir sekilde ayni arayuzu implement eder. "
                + "add() ile belgeler eklenir, embedding otomatik uretilir; similaritySearch() ile sorgu metnine en benzer belgeler skor sirasiyla dondurulerek RAG retrieval'i tamamlanir.",
                Map.of("lesson", l, "topic", "vector-store", "source", "b102-seed"))
        );
    }

    private List<Document> b103Docs() {
        String l = "b103";
        return List.of(
            doc("b103-rel-1",
                "Spring AI'da QuestionAnswerAdvisor, en temel RAG uygulamasinin merkezinde yer alan advisor sinifidir ve kullanicinin sorusuna VectorStore destekli yanit verilmesini saglar. "
                + "SearchRequest ile topK, threshold ve filtre parametrelerini alir; her prompt cagirisinda VectorStore'dan otomatik retrieval yapar. "
                + "Basit kullanim senaryolarinda tek basina yeterlidir ancak retriever ve augmenter uzerinde ince ayar gerektiginde modular yaklasima gecilmelidir. "
                + "ChatClient.defaultAdvisors() ile baglanir ve FILTER_EXPRESSION parametresiyle runtime'da filtrelenebilir. "
                + "Advisor kavrami bir ara katman gibi dusunulebilir: kullanicinin istegi ile LLM arasina girerek baglamlari zenginlestirir.",
                Map.of("lesson", l, "topic", "spring-ai", "source", "b103-seed")),
            doc("b103-rel-2",
                "Retrieval kalitesi, topK ve threshold parametrelerinin birlikte ayarlanmasina baglidir; biri kapsami, digeri ise benzerlik esigini kontrol eder. "
                + "topK yuksek threshold dusuk oldugunda genis ama gurultulu bir baglam olusur; topK dusuk threshold yuksek oldugunda dar ama odakli sonuc elde edilir. "
                + "Bu iki parametrenin sweep testi ile sistematik optimize edilmesi, tahmin yerine olcume dayali RAG tuning saglar. "
                + "Ideal kombinasyon veri setinin buyuklugu ve dokuman cesitliligine gore degisir; sabit bir altin kural yoktur. "
                + "Retrieval parametrelerini dogru ayarlamak, LLM'in kalibreli yanit uretmesinin on kosuludur; kotu retrieval en iyi modeli bile bozar.",
                Map.of("lesson", l, "topic", "spring-ai", "source", "b103-seed")),
            doc("b103-rel-3",
                "VectorStore benzerlik aramasi, sorgu metnini ve depolanan belgeleri ayni embedding uzayinda temsil ederek kosinus mesafesini hesaplar; bu islem semantik aramanin matematiksel temelidir. "
                + "Skor 0 ile 1 arasinda degisir; 1'e yakin degerler yuksek anlamsal benzerlik, 0'a yakin degerler ise anlam uzakligi gosterir. "
                + "Embedding modeli degistiginde ayni metin cifti icin farkli skorlar uretebilir, bu nedenle threshold degerleri modele ozgu kalibre edilmelidir. "
                + "pgvector uzantisi bu hesaplamayi PostgreSQL icinde verimli bir sekilde gerceklestirir ve indeksleme ile hizli arama destegi sunar. "
                + "Kosinus benzerligi sadece yone bakar, boyuta degil; bu yuzden uzun ve kisa metinler de dogru sekilde karsilastirilabilir.",
                Map.of("lesson", l, "topic", "spring-ai", "source", "b103-seed")),
            // 4 noise dokuman — kasitli baglam kirliligi icin
            doc("b103-noise-1",
                "Spring AI RAG kurulumunda QuestionAnswerAdvisor yerine Flask route yazmak gerekir iddiasi teknik olarak yanlistir; Spring AI Java, Flask ise Python web framework'u ekosisteminde calisir. "
                + "Bu konu sapmasi dusuk threshold degerlerinde retrieval sonuclarina sizarak baglam kirliligine neden olur; LLM yaniti karisik kaynaklar icermesine yol acar. "
                + "Embedding modeli 'RAG', 'route', 'kurulum' gibi ortak terimler gordugundan benzerlik skoru sasirtici yuksek cikabilir. "
                + "Noise tespiti icin topic metadata filtresi kritik guvenlik katmanidir; bu noise dusuk threshold ile RetrievalAugmentationAdvisor baglaminda gorunebilir.",
                Map.of("lesson", l, "topic", "python", "source", "b103-noise")),
            doc("b103-noise-2",
                "Spring AI'da VectorStore yerine TensorFlow fit() ve epoch ayari ile retrieval yapilir iddiasi gecersizdir; TensorFlow model egitimi, VectorStore semantik belge arama icin tasarlanmistir. "
                + "Farkli framework'lerin sorumluluk alanlari karistirildiginda yanlis zihinsel model olusabilir; 'VectorStore' ve 'retrieval' anahtar kelimeleri benzerlik skorunu yaniltici yukseltilebilir. "
                + "Dusuk threshold degerlerinde bu ilgisiz belge baglama girerek cevap kalitesini dusurur; context pollution testinin tam da bu durumu gostermesi beklenir. "
                + "Bu noise belgesi ml-framework etiketiyle isaretlenmistir; metadata filtresi devreye girdiginde Spring AI sorgularindan dislanir.",
                Map.of("lesson", l, "topic", "ml-framework", "source", "b103-noise")),
            doc("b103-noise-3",
                "RAG benzerligi embedding vektorleri arasindaki kosinus mesafesiyle olculur, HashMap hashCode mekanizmasiyla degil; hashCode ve kosinus benzerligi tamamen farkli kavramlardir. "
                + "hashCode tam esitlik kontrolu icin tasarlanmistir ve anlamsal yakinligi yakalayamaz; iki farkli kelime ayni hashCode'a bile sahip olabilir. "
                + "Bu yanlis iddia, Java temellerini bilen ama NLP ve embedding kavramlarindan habersiz bir baglam kirliligi ornegidir. "
                + "Retrieval sirasinda dusuk benzerlik esiginde bu tur yari-teknik noise baglama sizarak LLM yanitinin kalitesini dusurur ve hatali teknik aciklamalar yapmasina zemin hazirlar. "
                + "Bu dokuman java-core etiketiyle isaretlenmis bir noise ornegi olup metadata filtresi aktifken Spring AI sorgularinda gozukmemesi beklenir.",
                Map.of("lesson", l, "topic", "java-core", "source", "b103-noise")),
            doc("b103-noise-4",
                "QuestionAnswerAdvisor'in Kubernetes pod autoscaling yonettigi iddiasi tamamen yanlistir; QuestionAnswerAdvisor bir RAG advisor sinifi olup retrieval ve prompt zenginlestirmeyle ilgilenir, altyapi yonetimiyle degil. "
                + "Bu kasitli noise ornegi Spring AI anahtar kelimesini tasidigi icin embedding uzayinda gercek belgelerle yakin konumlanabilir. "
                + "Context pollution testinde bu belgenin retrieval listesinde gorunmesi dusuk threshold ve yuksek topK kombinasyonunun riskini somut olarak gosterir. "
                + "Modular RAG ve reranking bu tur noise'i etkili filtreleyebilir; metadata filtresi devops etiketiyle dislar ve naive RAG'in zayifligini ortaya koyar.",
                Map.of("lesson", l, "topic", "devops", "source", "b103-noise"))
        );
    }

    private List<Document> b104Docs() {
        String l = "b104";
        return List.of(
            doc("b104-advisor",
                "RetrievalAugmentationAdvisor, Spring AI'da modular RAG pipeline'inin orkestrasyon katmanidir ve QuestionAnswerAdvisor'a gore cok daha fazla kontrol saglar. "
                + "RetrievalAugmentationAdvisor nedir sorusunun cevabi: retriever, transformer, augmenter ve post-processor bileskenlerini tek pipeline'da siralayan ust seviye koordinator sinif. "
                + "documentRetriever(), queryTransformers(), documentPostProcessors() ve queryAugmenter() builder metotlariyla her adim bagimsiz yapilandirilabilir. "
                + "Pipeline sirasi pre-retrieval, retrieval, post-retrieval ve augmentation seklindedir; her adim izole test edilebildigi icin hata ayiklama ve optimizasyon kolaylasir.",
                Map.of("lesson", l, "topic", "advisor", "source", "b104-seed")),
            doc("b104-retriever",
                "VectorStoreDocumentRetriever, modular RAG pipeline'inda retrieval katmanini temsil eden siniftir ve topK, similarityThreshold ile filterExpression parametrelerini yonetir. "
                + "VectorStoreDocumentRetriever ne yapar sorusunun cevabi: VectorStore'a sorgu gonderir, benzerlik hesaplar ve esik ile adet sinirlarina gore adaylari secer. "
                + "QuestionAnswerAdvisor'daki SearchRequest'ten farki, bu parametrelerin ayri nesnede tanimlanarak izole test ve degisiklige acik olmasidir. "
                + "FILTER_EXPRESSION runtime parametresi ile dinamik metadata filtresi uygulanabilir; Builder ile olusturulur ve RetrievalAugmentationAdvisor'a documentRetriever() metodu ile baglanir.",
                Map.of("lesson", l, "topic", "retriever", "source", "b104-seed")),
            doc("b104-augmenter",
                "ContextualQueryAugmenter, retrieval sonuclarini LLM prompt'una enjekte eden augmentation katmaninin Spring AI implementasyonudur; RAG'in 'A' harfini, zenginlestirme adimini temsil eder. "
                + "ContextualQueryAugmenter ne ise yarar sorusunun cevabi: bulunan belgeleri anlamli formatta kullanici sorusuyla birlikte LLM'e sunar. "
                + "allowEmptyContext(false) yapildiginda, hicbir belge bulunamazsa LLM'in serbest uretim yapmasini engeller; model 'bilmiyorum' der, hallucination onlenir. "
                + "allowEmptyContext(true) ise modelin bilgi olmadan cevap uretmesine izin verir; prompt sablonlari ile belgelerin hangi formatta eklenecegi de ozellestirilebilir.",
                Map.of("lesson", l, "topic", "augmenter", "source", "b104-seed")),
            doc("b104-modular-rag",
                "Modular RAG mimarisi, retrieval, augmentation ve generation adimlarini ayri bilesenler olarak tanimlayarak her birini bagimsiz sekilde yapilandirma, test etme ve degistirme imkani saglar. "
                + "Modular RAG neden tercih edilir sorusunun cevabi: her adim izole edilebilir, sorun tespiti kolaylasir ve bireysel bileskenler bagimsiz degistirilebilir. "
                + "Naive RAG'de QuestionAnswerAdvisor tum adimlari icinde gizler; hata hangi adimda olustu anlasilamaz, tuning kor deneme yanilmaya donusur. "
                + "Modular yaklasimda retriever degistirilebilir, augmenter ozellestirilir, reranking eklenebilir; ancak ek karmasiklik maliyeti vardir, once naive deneyin.",
                Map.of("lesson", l, "topic", "modular-rag", "source", "b104-seed")),
            doc("b104-naive-rag",
                "Naive RAG yaklasimi QuestionAnswerAdvisor ile tek satirda kurulabilir ve hizli prototipleme icin idealdir; retrieval ile generation arasinda hicbir ek katman bulunmaz. "
                + "Naive RAG ne zaman yeterlidir sorusunun cevabi: kucuk veri setinde, basit sorgularda ve gizlilik gerekmediginde tek advisor yeterli olabilir. "
                + "Ancak retriever, augmenter ve post-processing adimlari tek bir soyutlama icinde gizli oldugu icin sorun tespiti ve ince ayar zorlasir. "
                + "Retrieval sonuclarinin kalitesini incelemek icin ayri bir test yazmak gerekir cunku advisor ic akisini disari vurmaz; kotu belge secimi gorunmez kalir. "
                + "Production'a geciste genellikle RetrievalAugmentationAdvisor'a gecis onerilir; bu gecis ek kontrol ve gozlemlenebilirlik saglar.",
                Map.of("lesson", l, "topic", "naive-rag", "source", "b104-seed"))
        );
    }

    private List<Document> b105Docs() {
        String l = "b105";
        return List.of(
            doc("b105-rewrite",
                "RewriteQueryTransformer, kullanicinin belirsiz veya kisa sorgusunu bir LLM cagrisi ile retrieval icin optimize edilmis acik bir ifadeye donusturur. "
                + "RewriteQueryTransformer neden kullanilir sorusunun cevabi: kullanici sorusu kisa ve baglamsizdır; rewrite ile embedding aramasinda daha isabetli sonuc verecek sekilde yeniden yazilir. "
                + "Ornegin 'bu ne' gibi bir sorgu 'Spring AI framework nedir ve ne ise yarar?' seklinde yeniden yazilabilir; VectorStore boylece dogrudan konuyla ilgili belgeleri getirir. "
                + "Her transform() cagrisi bir LLM istegi tetikler; latency ve maliyet artisi goz onunde tutulmali, yalnizca gercekten belirsiz sorgularda etkinlestirilmelidir.",
                Map.of("lesson", l, "topic", "rewrite", "source", "b105-seed")),
            doc("b105-multi-query",
                "MultiQueryExpander, tek bir kullanici sorusundan N farkli ifade uretir ve her varyant icin bagimsiz retrieval calistirir; tek sorgunun kacirdigi belgeler de yakalanir. "
                + "MultiQueryExpander ne zaman kullanilir sorusunun cevabi: tek ifadeyle embedding aramasinin yetersiz kaldigi, kapsamli ve cesitli retrieval istenen durumlarda. "
                + "numberOfQueries varyant sayisini belirler; includeOriginal(true) orijinal sorguyu da listeye dahil eder, recall artar. "
                + "Dikkat: her ek varyant ekstra LLM cagrisi ve retrieval demektir; maliyeti hakli kilmayan sorgularda kullanma.",
                Map.of("lesson", l, "topic", "multi-query", "source", "b105-seed")),
            doc("b105-retrieval",
                "Query donusturuculeri (rewrite ve expand), retrieval adiminda isabetli belge bulma oranini arttirir cunku kullanicinin ham ifadesi embedding aramasi icin yeterince spesifik degildir. "
                + "Pre-retrieval donusumu neden onemlidir sorusunun cevabi: kullanici sorusu conversational tondayken embedding aramasi semantik aciklik ister; bu boslugu transformer kapatir. "
                + "RewriteQueryTransformer ile sorgu netlestirildikten sonra MultiQueryExpander ile genisletilir; bu iki adim birlikte hem precision hem recall uzerinde olumlu etki yapar. "
                + "Optimum strateji sorgu tipine baglidir: basit sorgularda yalnizca rewrite yeterli, karmasik ve cok anlamli sorgularda expander da eklenir.",
                Map.of("lesson", l, "topic", "retrieval", "source", "b105-seed")),
            doc("b105-compression",
                "CompressionQueryTransformer, cok turlu sohbet gecmisini analiz ederek belirsiz son soruyu onceki baglamla birlestirip tek bir bagimsiz, anlamli sorguya donusturur. "
                + "CompressionQueryTransformer hangi sorunu cozer sorusunun cevabi: cok turlu sohbette kullanici 'daha anlat' gibi baglamli sorular sorar; transformer onceki konuyla birlestirip retrieval icin anlamli hale getirir. "
                + "Ornegin 'daha fazla anlat' sorgusunu 'Spring AI framework hakkinda daha fazla bilgi ver' gibi tam bir ifadeye cevirir. "
                + "Query.builder().text(q).history(messages).build() ile gecmis aktarilir; transformer dahili LLM cagrisiyla baglam cikarir ve sorguyu yeniden yazar.",
                Map.of("lesson", l, "topic", "compression", "source", "b105-seed")),
            doc("b105-rag",
                "RetrievalAugmentationAdvisor, query transformer, expander, document retriever, post-processor ve augmenter bilessenlerini tek bir pipeline'da otomatik siralayan ust katman sinifidir. "
                + "Bu advisor neden gucludur sorusunun cevabi: her bilesenin birbirinden bagimsiz yapilandirilmasina izin verirken hepsini koordineli bir akis olarak calistirir. "
                + "queryTransformers() ile pre-retrieval, documentRetriever() ile retrieval, documentPostProcessors() ile post-retrieval ve queryAugmenter() ile augmentation adimi tanimlanir. "
                + "Bu zincir her prompt().call() cagirisinda otomatik isletilir; her adim maliyet ve latency artisi getirir ama RAG kalitesini sistematik olarak iyilestirir.",
                Map.of("lesson", l, "topic", "rag", "source", "b105-seed"))
        );
    }

    private List<Document> b106Docs() {
        String l = "b106";
        return List.of(
            doc("b106-topk",
                "Manual kaynak notu: topK parametresi arttirildiginda retrieval kapsami genisler ve daha fazla belge baglamda yer alir, ancak ilgisiz belgelerin dahil olma riski paralel artar. "
                + "topK nedir sorusunun cevabi: VectorStore'dan kac aday belgenin donecegini sinirlar; retrieval kapsaminin birincil kontrolcusu. "
                + "topK=1 en secici ayardir ve yalnizca en benzer belgeyi getirir; topK=10 genis aday havuzu olusturur ama noise riski artar. "
                + "Precision-recall dengesi icin sweep testi ile belirlemek en saglikli yaklasimdir; baslangicta topK=5 ile deneyin.",
                Map.of("lesson", l, "source", "manual", "topic", "topk")),
            doc("b106-threshold",
                "Manual kaynak notu: similarityThreshold yukseltildiginde yalnizca yuksek benzerlik skoruna sahip belgeler sonuca dahil edilir, bu precision'i arttirir ancak recall duser. "
                + "threshold tradeoff sorusunun cevabi: dusuk esik daha fazla belge ama daha cok noise; yuksek esik daha az belge ama daha yuksek ilgi orani demektir. "
                + "0.9 esigi cok secicidir ve neredeyse birebir eslesen belgeler gecer; 0.5 esigi gevsek olup ilgisiz belgeler baglama girebilir. "
                + "Threshold sifir yapildiginda tum topK adayi geri doner; bu strateji ozellikle post-retrieval reranking ile birlikte tercih edilir ve production'da 0.7 dengeli bir baslangic saglar.",
                Map.of("lesson", l, "source", "manual", "topic", "threshold")),
            doc("b106-retrieval",
                "Docs kaynak notu: retrieval kalitesi, RAG pipeline'inin overall performansini dogrudan belirler; LLM yalnizca kendisine saglanan baglamdaki bilgiye dayanir, en iyi model bile kotu retrieval ile basarisiz olur. "
                + "Retrieval tuning neden kritiktir sorusunun cevabi: yanlis veya eksik belge gelindiginde LLM bos baglam uzerinden yanit uretmeye calisir; hallucination veya 'bilmiyorum' kacinilmaz olur. "
                + "Kotu retrieval parametreleri ya ilgisiz belgeler baglama girer ya da ilgili belgeler kacrilir; her iki durum kullanici deneyimini olumsuz etkiler. "
                + "topK, threshold ve metadata filtrelerinin sweep testi ile sistematik optimizasyonu sezgisel tahminden cok daha guvenilir sonuc verir.",
                Map.of("lesson", l, "source", "docs", "topic", "retrieval")),
            doc("b106-threshold-docs",
                "Docs kaynak notu: similarityThreshold icin 0.7 degeri, cogu RAG senaryosunda dengeli bir baslangic noktasi olarak onerilir cunku ne cok secici ne de cok gevsek filtreleme saglar. "
                + "0.7 neden standart baslangic degeri sorusunun cevabi: pratik denemelerde yeterli precision saglarken recall'u asiri kisitlamayan denge noktasi olarak kabul gormustur. "
                + "Bu deger embedding modeline baglidir; farkli modeller ayni metin cifti icin farkli skor dagilimlari uretir, 0.7 evrensel altin kural degildir. "
                + "OpenAI ile 0.7 iyi calisirken baska bir model icin 0.6 veya 0.8 gerekebilir; modeli degistirince kalibrasyon tekrarlanmali ve 10-20 sorguyla dogrulanmalidir.",
                Map.of("lesson", l, "source", "docs", "topic", "threshold")),
            doc("b106-optimization",
                "Blog kaynak notu: RAG parametre optimizasyonu, veri seti buyudukce ve dokuman cesitliligi arttikca cok daha kritik hale gelir; varsayilan parametreler buyuk olcekte yetersiz kalir. "
                + "Veri seti buyudugunde parametreler neden yeniden ayarlanmali sorusunun cevabi: daha fazla dokuman daha yuksek noise riski demektir; topK ve threshold sabit kalmak yerine veriyle evrilmelidir. "
                + "100 belgeyle topK=5 iyi calisabilir ancak 10.000 belgede ayni parametre gurultu icinden gecmek demektir; oransal dusunmek gerekir. "
                + "Sistematik sweep testleri parametrelerin nesnel davranisini olcmenizi saglar; buyume planlanirken parametre stratejisi de birlikte planlanmalidir.",
                Map.of("lesson", l, "source", "blog", "topic", "optimization"))
        );
    }

    private List<Document> b107Docs() {
        String l = "b107";
        return List.of(
            doc("b107-token-text-splitter",
                "TokenTextSplitter, uzun metinleri belirli token sayisina gore parcalayarak VectorStore'a uygun boyutta chunk'lar olusturur; LLM context penceresi asimini onleyen on isleme adimi olarak calisir. "
                + "TokenTextSplitter nasil calisir sorusunun cevabi: chunkSize token siniriyla parcalar, ardisik parcalar arasinda overlapSize kadar tekrar eden token birakarak baglam kopmasini en aza indirir. "
                + "Overlap sayesinde bir cumlenin iki chunk arasinda bolunmesi durumunda baglam kaybi azaltilir; retrieval sirasinda yari bolunmus anlam kayiplari onlenir. "
                + "TokenTextSplitter, Spring AI ETL pipeline'inda DocumentTransformer olarak kullanilir ve ozellikle PDF gibi uzun belgelerin indexlenmesinde kritik rol oynar.",
                Map.of("lesson", l, "source", "manual", "topic", "token-text-splitter")),
            doc("b107-vector-search",
                "VectorStore semantic search, sorgu metnini ve depolanan belgeleri ayni embedding uzayinda temsil ederek aralarindaki kosinus benzerligini hesaplar; anlam odakli aramanin temel yontemidir. "
                + "Semantic search neden tek basina yetmez sorusunun cevabi: TokenTextSplitter gibi teknik terimler embedding uzayinda farkli konumlanabilir; sorgu tam terimi icermiyorsa ilgili belge atlanabilir. "
                + "'Java kurulumu' sorgusunun 'JDK yukleme adimlari' belgesini bulmasini saglar; ama 'TokenTextSplitter' sorgusu icin tam kelime eslesmesi gerektiren keyword arama cok daha isabetlidir. "
                + "Bu zayiflik keyword arama ile birlestirilerek hibrit arama stratejisiyle giderilir; iki yontem birbirinin kor noktasini kapatir.",
                Map.of("lesson", l, "source", "docs", "topic", "vector-search")),
            doc("b107-keyword-search",
                "Full-text keyword arama, PostgreSQL'in to_tsvector ve plainto_tsquery fonksiyonlari ile dokuman icerigindeki terimleri token dizisine cevirerek tam eslestirme yapar; anlam degil, harf dizisi odaklidir. "
                + "Keyword search ne zaman ustundur sorusunun cevabi: TokenTextSplitter gibi API isimleri ve sinif adlarinda tam kelime eslesmesi gerektiren sorgularda embedding'den belirgin sekilde daha isabetlidir. "
                + "BM25 benzeri ts_rank, terimin sikligina ve konumuna gore logaritmik ilgi puani hesaplar; TokenTextSplitter ve RetrievalAugmentationAdvisor gibi terimler icin bu yaklasim cok daha isabetlidir. "
                + "Dezavantaji anlamsal yakinligi yakalayamamasidir; 'belge parcalama araci' sorgusunda TokenTextSplitter'i bulamaz cunku kelime eslestirmesi bazlidir.",
                Map.of("lesson", l, "source", "docs", "topic", "keyword-search")),
            doc("b107-hybrid-search",
                "Hybrid search, semantic ve keyword arama sonuclarini Reciprocal Rank Fusion (RRF) algoritmasi ile birlestirerek her iki yontemin guclu yanlarini tek bir siralama listesinde bulusturur. "
                + "Hybrid search neden tercih edilir sorusunun cevabi: semantic arama anlam yakinligini, keyword arama tam terim eslesmesini yakalar; hibrit yontem her iki kor noktayi ortadan kaldirir. "
                + "RRF formulu 1/(k+rank) ile her iki listeden gelen siralamayi puanlar; her iki listede ust siralarda yer alan belgeler daha yuksek birlesik skor alir. "
                + "k=60 sabiti (Cormack 2009) rank farklarini yumusatir; hem TokenTextSplitter gibi tam terimler hem de 'belge bolme araci' gibi anlamsal ifadeler dogru eslenir.",
                Map.of("lesson", l, "source", "manual", "topic", "hybrid-search")),
            doc("b107-rag",
                "RAG pipeline'inda LLM'in urettigi yanit kalitesi, retrieval adiminda gelen belgelerin siralamasina ve ilgisine dogrudan baglidir; dogru belge yanlis sirayla gelse bile yanit kalitesi dusurebilir. "
                + "Neden dogru siralama onemlidir sorusunun cevabi: LLM'ler 'lost in the middle' etkisine maruz kalir; listenin ortasindaki ilgili bilgi model tarafindan gozden kacabilir. "
                + "En ilgili belge listenin basinda yer aldiginda model daha dogru ve tutarli yanitlar uretir; reranking ve RRF gibi siralama iyilestirme teknikleri cevap kalitesini somut arttirir. "
                + "Iyi bir RAG sistemi sadece dogru belgeleri bulmakla kalmaz, onlari dogru sirada sunar ve LLM dikkatini en ilgili bilgiye yonlendirir.",
                Map.of("lesson", l, "source", "blog", "topic", "rag"))
        );
    }

    private List<Document> b108Docs() {
        String l = "b108";
        return List.of(
            doc("b108-embedding",
                "Embedding, dogal dil metnini sabit boyutlu sayisal vektore donusturerek anlamsal benzerliklerin hesaplanabilir hale gelmesini saglar; semantik aramanin matematik altyapisini olusturur. "
                + "Embedding nedir sorusunun cevabi: metni sayisal koordinatlara ceviren donusum; anlam benzer kelime ve cumleler bu koordinat uzayinda birbirine yakin noktalarda yer alir. "
                + "OpenAI text-embedding-ada-002 gibi bir model her cumleyi yuksek boyutlu uzayda temsil eder; anlamca yakin ifadeler bu uzayda birbirine yakin noktalar olusturur. "
                + "Kosinus benzerligi vektorler arasindaki aciyi olcerek 0-1 arasi skor uretir; VectorStore bu embedding'leri depolar ve similaritySearch ile en yakin komsulari skor sirasiyla dondurur.",
                Map.of("lesson", l, "topic", "embedding", "source", "b108-seed")),
            doc("b108-vector-store",
                "VectorStore, Spring AI'da embedding vektorlerini kalici olarak depolayan ve kosinus benzerligi ile hizli arama yapan arayuzdur; RAG mimarisinin veri katmanini olusturur. "
                + "VectorStore ile geleneksel veritabani farkinin cevabi: klasik veritabani tam kelime eslestirir, VectorStore anlam yakinligini hesaplar; 'araba tamir' sorgusunda 'otomobil servis' sonucunu getirebilir. "
                + "PgVectorStore, PostgreSQL pgvector uzantisiyla milyonlarca vektoru indexler; add() ile ekleme sirasinda embedding otomatik uretilir, similaritySearch() ile sorgu vektoru karsilastirilir. "
                + "Reranking senaryosunda VectorStore genis adayi saglar, ardindan cross-encoder veya LLM tabanli reranker sirayi iyilestirir.",
                Map.of("lesson", l, "topic", "vector-store", "source", "b108-seed")),
            doc("b108-reranking",
                "Reranking, vektorel arama sonucunda gelen ilk adaylari yeniden siralayarak en ilgili belgelerin listenin basina tasinmasini saglar; retrieval kalitesini bir adim daha ileri tasir. "
                + "Reranking neden gereklidir sorusunun cevabi: bi-encoder hiz icin optimize edilmis kaba siralama yapar; reranker her sorgu-belge ciftini derin analiz ederek cok daha hassas ilgi puani uretir. "
                + "'Wide retrieval, narrow output' stratejisi: genis topK ile cok aday al, reranker ile en iyileri sec; hem recall hem precision optimize edilir. "
                + "Spring AI'da DocumentPostProcessor arayuzu ile reranking katmani RetrievalAugmentationAdvisor pipeline'ina yeni bir adim olarak entegre edilir.",
                Map.of("lesson", l, "topic", "reranking", "source", "b108-seed")),
            doc("b108-java",
                "Java Stream API, koleksiyon islemlerinde fonksiyonel programlama stilini Java ekosistemine tasiyan guclu bir aractir ve lambda ifadeleriyle okunabilir kod uretir. "
                + "map(), filter(), sorted() ve collect() gibi ara ve terminal operasyonlariyla veri donusumleri bildirimsel sekilde yazilir. "
                + "Spring AI'da Document listelerini isleme ve skor bazli filtreleme icin kullanilir; stream().filter(d -> d.getScore() > threshold) gibi idiomatik kod saglar. "
                + "Bu belge reranking demosunda kasitli eklenmis dusuk ilgili bir icerik ornegi olup reranker tarafindan dusuk skor alarak liste altina itilmesi beklenir.",
                Map.of("lesson", l, "topic", "java", "source", "b108-seed")),
            doc("b108-sports",
                "Futbolda orta saha kontrolu oyunun temposunu dogrudan belirler; topa sahip olan takim oyunun ritimini yonetir ve rakibin duzensiz savunma yapmasina zemin hazirlar. "
                + "Bu belge Spring AI veya RAG konusuyla hicbir ilgisi olmayan kasitli bir noise ornegi olarak eklenmistir; tamamen farkli bir bilgi alanina aittir. "
                + "Reranking algoritmasinin bu belgeye dusuk ilgi puani vererek listenin en altina itmesi beklenir; RAG sorusuna verilen yanitda bu icerik yer almamalidir. "
                + "Embedding uzayinda bile dusuk benzerlik skoru almasi gereken bu dokuman, reranking'in noise filtrelemesindeki etkisini somut olarak gosterir. "
                + "Bu noise belgesinin varligi, reranker'in yalnizca yuksek skor degil, gercek konu iliskisine gore siralama yaptigini kanitlamak icin tasarlanmistir.",
                Map.of("lesson", l, "topic", "sports", "source", "b108-noise"))
        );
    }

    private List<Document> b109Docs() {
        String l = "b109";
        return List.of(
            doc("b109-simple-vector-store",
                "Spring AI'da SimpleVectorStore, embedding vektorlerini JVM heap memory'de saklayan hafif ve harici bagimlilik gerektirmeyen bir VectorStore implementasyonudur. "
                + "SimpleVectorStore ne zaman kullanilir sorusunun cevabi: veritabani kurmadan hizli prototipleme, birim testleri ve kucuk olcekli demonstrasyonlar icin idealdir. "
                + "Uygulama yeniden baslatildiginda tum veriler kaybolur; save() ve load() metotlari ile JSON tabanli dosya kaliciligi destegi mevcuttur. "
                + "Production ortaminda PgVectorStore veya ChromaVectorStore gibi kalici ve olceklenebilir implementasyonlar tercih edilmelidir.",
                Map.of("lesson", l, "source", "spring-ai-docs.pdf", "page", "15", "format", "pdf")),
            doc("b109-pgvector-store",
                "PgVectorStore, PostgreSQL veritabaninin pgvector uzantisini kullanarak embedding vektorlerini kalici ve olceklenebilir sekilde depolar; production RAG'in tercih edilen omurgasidir. "
                + "PgVectorStore neden onerilir sorusunun cevabi: PostgreSQL olgunlugu, JSONB metadata filtreleme, SQL sorgulama ve HNSW ile hizli vektor aramasi bir arada sunar. "
                + "HNSW ve IVFFlat indexleme ile milyonlarca vektorde hizli kosinus benzerligi aramasi yapilabilir; indexleme stratejisi veri boyutuna gore secilir. "
                + "Spring AI VectorStore arayuzunu implement eder; add(), delete() ve similaritySearch() sayesinde diger implementasyonlarla minimal degisimle yer degistirilebilir.",
                Map.of("lesson", l, "source", "spring-ai-docs.pdf", "page", "18", "format", "pdf")),
            doc("b109-chroma-vector-store",
                "ChromaVectorStore, Chroma veritabanini kullanan hafif bir VectorStore implementasyonu olup kucuk-orta olcekli projelerde ve hizli prototipleme ortamlarinda tercih edilir. "
                + "ChromaDB ne zaman secilir sorusunun cevabi: PostgreSQL altyapisi kurmadan hizlica bir vektor deposu saglamak istediginde; tek komutla Docker container baslatilir ve REST API uzerinden aninda kullanima hazirdir. "
                + "PgVectorStore'a gore daha basit altyapi gerektirir ancak PostgreSQL'in JSONB filtreleme, SQL yedekleme ve olceklenebilirlik ozelliklerinden yoksundur. "
                + "Spring AI ekosisteminde farkli VectorStore seceneklerinin bulunmasi proje gereksinimlerine ve altyapi tercihine gore esneklik saglar.",
                Map.of("lesson", l, "source", "architecture-notes.md", "page", "N/A", "format", "md")),
            doc("b109-citation",
                "Citation (kaynak gosterimi), RAG cevabinin hangi belge, sayfa veya dosyadan turetildigini acikca belirterek sistemi denetlenebilir ve guvenilir kilar. "
                + "Citation neden gereklidir sorusunun cevabi: kullanici iddiay dogrulamak istediginde kaynaga basvurabilmeli; citation ile her bilgi parcasinin arkasinda somut referans bulunur. "
                + "[Kaynak: dosya_adi, Sayfa: X] formatinda citation bilgi seffafligi saglar; temperature=0.0 ile deterministik yanit uretimi citation formatinin tutarliligini arttirir. "
                + "Finans, saglik ve hukuk gibi regulasyona tabi alanlarda kaynak gosterilebilirlik yasal zorunluluk olabilir; bu metadata tam olarak bu ihtiyaci karsilar.",
                Map.of("lesson", l, "source", "ops-handbook.txt", "page", "N/A", "format", "txt"))
        );
    }

}
