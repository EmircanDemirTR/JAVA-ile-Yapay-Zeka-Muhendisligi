# Java ile Yapay Zeka Mühendisliği: Spring AI, RAG ve LLM

Bu repo, **Udemy** için hazırlanan Java ile Yapay Zeka Mühendisliği eğitiminin bölüm bazlı uygulama kodlarını içerir.

> Kursa hemen katılmak için: [Udemy kurs sayfası](https://www.udemy.com/course/java-ile-yapay-zeka-muhendisligi/) | [İndirimli kayıt linki (`JAVAYAPAYZEKA`)](https://www.udemy.com/course/java-ile-yapay-zeka-muhendisligi/?couponCode=JAVAYAPAYZEKA)


## Eğitim Hakkında

Bu eğitimde Spring AI ekosistemi üzerinde LLM entegrasyonu, RAG mimarisi, function calling, MCP, bellek, multimodal akışlar ve üretim güvenliği/optimizasyon konuları uçtan uca ele alınmaktadır.

## Program İçeriği (Güncel)

Aşağıdaki bölüm ve ders listesi, paylaşılan indeks dosyasına göre güncellenmiştir.

| Bölüm No | Bölüm Adı | Ders Adedi |
|---|---|---:|
| 1 | Temel Kavramlar | 5 |
| 2 | Geliştirme Ortamı ve Yerel Model Kurulumu | 7 |
| 3 | ChatClient API ve LLM Entegrasyonu | 6 |
| 4 | Prompt Mühendisliği ve Template Yönetimi | 4 |
| 5 | Yapılandırılmış Çıktı (Structured Output) | 5 |
| 6 | Function Calling ve Tool Entegrasyonu | 6 |
| 7 | MCP (Model Context Protocol) Entegrasyonu | 5 |
| 8 | Advisor Mimarisi ve Akıllı İş Akışları | 4 |
| 9 | Embedding, Vektör Veritabanları ve ETL Pipeline | 12 |
| 10 | RAG Mimarisi ve Uygulaması | 10 |
| 11 | Gelişmiş RAG Desenleri ve Agentic RAG | 5 |
| 12 | Sohbet Belleği ve Konuşma Yönetimi | 5 |
| 13 | Multimodal AI Uygulamaları | 5 |
| 14 | Güvenlik ve Optimizasyon | 4 |

**Toplam bölüm:** 14
**Toplam ders:** 83

## Detaylı Ders Listesi

### 1.Temel Kavramlar (5 ders)

- 1.1 Bilgilendirme
- 1.2 LLM, Token ve Context Window: Bilinmesi Gerekenler
- 1.3 Embedding, Vektör Arama ve RAG: Temel Mimari
- 1.4 Hallucination, Temperature ve Top-P: LLM Davranış Parametreleri
- 1.5 Yerel vs Bulut Modeller: Hugging Face ve Ollama Ekosistemi

### 2.Geliştirme Ortamı ve Yerel Model Kurulumu (7 ders)

- 2.1 JDK 25 ve Intellij IDEA Kurulumu
- 2.2 Spring Initializr ile Spring AI 2.0 Projesi Oluşturmak
- 2.3 Maven Bağımlılıkları ve API Anahtarı Yapılandırması
- 2.4 Ollama Kurulumu ve Yerel LLM Modeli Çalıştırmak
- 2.5 Hugging Face'ten Model İndirmek, Quantization (GGUF) ve Ollama'ya Yüklemek
- 2.6 Docker ile Geliştirme Ortamı: pgvector, ChromaDB, Redis
- 2.7 Swagger UI, OpenAPI ve REST API Test Araçları

### 3.ChatClient API ve LLM Entegrasyonu (6 ders)

- 3.1 ChatClient API ve Builder Pattern ile İlk Uygulamayı Yazmak
- 3.2 OpenAI Modelleri ile Sohbet Uygulaması Geliştirmek
- 3.3 Ollama ile Yerel Modellere Bağlanmak (Llama, DeepSeek vb.)
- 3.4 call() vs stream(): SSE ile Gerçek Zamanlı Streaming Uygulaması
- 3.5 ChatResponse ile Metadata, Token Kullanımı ve Maliyet İzleme
- 3.6 Model Sağlayıcılar Arası Geçiş ve Hata Yönetimi Stratejisi

### 4.Prompt Mühendisliği ve Template Yönetimi (4 ders)

- 4.1 PromptTemplate ile Dinamik Prompt ve System Prompt Oluşturmak
- 4.2 Prompt Şablonlarını Dosyadan Yüklemek ve Versiyonlama
- 4.3 Prompt Mühendisliği Teknikleri: Zero-Shot, Few-Shot ve Chain-of-Thought
- 4.4 Advisor Zincirinde Prompt Yönetimi: Default System ve Runtime Override

### 5.Yapılandırılmış Çıktı (Structured Output) (5 ders)

- 5.1 BeanOutputConverter ile Model Çıktısını POJO'ya Dönüştürmek
- 5.2 ListOutputConverter ve MapOutputConverter ile Çalışmak
- 5.3 entity() Metodu ile Otomatik Dönüşüm Uygulamak
- 5.4 Nested Nesneler, Enum ve Polimorfik Tipler ile Çalışmak
- 5.5 JSON Schema Kontrolü ve Hata Yönetimi

### 6.Function Calling ve Tool Entegrasyonu (6 ders)

- 6.1 Function Calling: LLM'in Araç Çağırma Prensibini Uygulamak
- 6.2 @Tool Anotasyonu ile İlk Tool Yazmak ve Bağlamak
- 6.3 Dış API ve Veritabanı Sorgulama: Kur Dönüşümü ve Harcama Kaydı
- 6.4 Dış API ve Veritabanı Sorgulama: Hesap Makinesi, Asistan Orkestrasyonu
- 6.5 Tool Dönüş Tiplerini ve Hata Yönetimini Yapılandırmak
- 6.6 Dinamik Tool Yönetimi ve Gerçek Zamanlı Veri Akışı

### 7.MCP (Model Context Protocol) Entegrasyonu (5 ders)

- 7.1 MCP Protokolü ve Transport Yapıları (STDIO, SSE)
- 7.2 MCP Client ile Mevcut Sunuculara Bağlanmak
- 7.3 Kendi MCP Server'ınızı Oluşturmak
- 7.4 MCP Tool'larını ChatClient'a Dinamik Olarak Bağlamak
- 7.5 Çoklu MCP Sunucu Entegrasyonu

### 8.Advisor Mimarisi ve Akıllı İş Akışları (4 ders)

- 8.1 Advisor Mimarisi: CallAdvisor ve StreamAdvisor
- 8.2 SafeGuardAdvisor ile Girdi/Çıktı Güvenliği
- 8.3 Chain Workflow ve Routing Pattern Uygulamak
- 8.4 Parallelization Workflow: Eşzamanlı LLM İşlemleri

### 9.Embedding, Vektör Veritabanları ve ETL Pipeline (12 ders)

- 9.1 Embedding ve Benzerlik Vektörü
- 9.2 Metin Vektörleri Oluşturmak
- 9.3 OpenAI ve Local Embedding Modelleriyle Karşılaştırma
- 9.4 VectorStore ve SimpleVectorStore
- 9.5 PostgreSQL pgvector Entegrasyonu
- 9.6 ChromaDB ile Vektör Deposu ve Karşılaştırma
- 9.7 ETL Pipeline: Reader, Transformer ve Writer Bileşenleri
- 9.8 Markdown, JSON, TXT ve PDF Dosyalarını Okumak (TikaDocumentReader)
- 9.9 TokenTextSplitter: Bölme Stratejileri ve Overlap
- 9.10 Metadata Zenginleştirme ve Filtre Bazlı Arama
- 9.11 Uçtan Uca ETL Pipeline Yapısı
- 9.12 Retriever ile Sorgular Yapmak

### 10.RAG Mimarisi ve Uygulaması (10 ders)

- 10.1 RAG Mimarisi: Temel Bileşenler ve Akış
- 10.2 Ortak Seed Hazırlama
- 10.3 İlk RAG Uygulaması
- 10.4 Naive RAG Problemleri: Hallucination, Bağlam Kirliliği
- 10.5 Modular RAG Mimarisini Yapılandırmak
- 10.6 Sorgu Dönüştürme: Rewrite, Expander ve Sohbet Bağlamı Transformer
- 10.7 Similarity Threshold, Top-K Ayarlama ve Retriever Metrikleri
- 10.8 Hibrit Arama: Vector Search + Keyword Search
- 10.9 Reranking ile RAG Pipeline
- 10.10 Çok Kaynaklı RAG ve Citation (Kaynak Gösterme)

### 11.Gelişmiş RAG Desenleri ve Agentic RAG (5 ders)

- 11.1 Corrective RAG (CRAG): Doküman Kalite Puanlama ve Fallback Stratejisi
- 11.2 Self-RAG: LLM'in Kendi Çıktısını Değerlendirmesi ve Retrieval'a Geri Dönmesi
- 11.3 Adaptive RAG: Sorgu Karmaşıklığına Göre Strateji Seçimi
- 11.4 Agentic RAG: Otomatik Tool Seçimi ve Retrieval
- 11.5 Recursive Advisor ile ReAct Pattern

### 12.Sohbet Belleği ve Konuşma Yönetimi (5 ders)

- 12.1 ChatMemory API: Bellek Türleri ve Stratejileri
- 12.2 ChatMemoryRepository ve MessageWindowChatMemory Kullanımı
- 12.3 Chat Memory Advisor'u ChatClient'a Eklemek
- 12.4 Veritabanı Tabanlı Kalıcı Chat Belleği
- 12.5 Chat Belleği + RAG Birleşimi

### 13.Multimodal AI Uygulamaları (5 ders)

- 13.1 Multimodal API: Görsel, Ses ve Metin Desteği
- 13.2 Görsel Analizi ve Yapılandırılmış Veri Çıkarma
- 13.3 Ses İşleme: Metinden Sese ve Sesten Metne Dönüşüm, Sesli Asistan
- 13.4 Görsel Üretimi, İndeksleme ve Multimodal RAG
- 13.5 Ollama ile Yerel Multimodal Modeller (LLaVA, Moondream)

### 14.Güvenlik ve Optimizasyon (4 ders)

- 14.1 Prompt Injection Önleme ve PII Maskeleme
- 14.2 AI Endpoint Güvenliği: Token ile Giriş ve Rate Limiting
- 14.3 Caching ve Maliyet Optimizasyonu
- 14.4 İzlenebilirlik: Micrometer, Tracing ve Grafana Arayüzü

## Teknik İçerik Başlıkları

- Spring AI (OpenAI, Ollama, Anthropic)
- Prompt engineering ve structured output
- Function calling ve tool orchestration
- Embedding, vector store, ETL ve RAG
- Agentic RAG, chat memory, multimodal
- Rate limiting, retry, cache, observability

## Ön Koşullar

- Java 25
- Maven 3.9+
- Docker + Docker Compose
- (Opsiyonel) Ollama

## Kaynak

## Eğitim Linkleri

- [Udemy kurs sayfası](https://www.udemy.com/course/java-ile-yapay-zeka-muhendisligi/)
- [İndirimli kayıt linki (`JAVAYAPAYZEKA`)](https://www.udemy.com/course/java-ile-yapay-zeka-muhendisligi/?couponCode=JAVAYAPAYZEKA)

## Lisans

Bu kodlar eğitim amaçlı hazırlanmıştır. Kaynak göstererek kullanabilirsiniz.
