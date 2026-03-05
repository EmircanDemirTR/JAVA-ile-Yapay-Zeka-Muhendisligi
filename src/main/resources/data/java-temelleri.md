# Java Temelleri ve Uygulama Pratikleri

Java, nesne yonelimli programlamayi guclu bir tip sistemi ile birlestiren bir dildir.
Siniflar, nesneler, interface yapilari ve paketleme modeli sayesinde buyuk projelerde duzenli gelistirme saglar.
Ozellikle Spring Boot gibi framework'lerle birlikte kullanildiginda,
kurumsal uygulama gelistirme sureci oldukca hizlanir.

## Temel Kavramlar

- **Class ve Object:** Class bir kalip, object bu kalibin calisan ornegidir. Her Java programi en az bir sinif icerir.
- **Encapsulation:** Veriyi kontrollu sekilde aciga cikarmak icin access modifier kullanilir. Private alanlar getter/setter ile erisime acilir.
- **Inheritance:** Ortak davranislar ust siniftan alt siniflara aktarilir. Java'da tekli miras desteklenir, coklu miras interface ile saglanir.
- **Polymorphism:** Ayni metot farkli tiplerde farkli davranis sergileyebilir. Override ile calisma zamaninda hangi metodun cagrilacagi belirlenir.
- **Abstraction:** Karmasik iç detaylari gizleyerek sadece gerekli arayuzu sunma prensibidir. Abstract class ve interface bu amacla kullanilir.

## Koleksiyonlar

Java Collections Framework, veri yapilarini standart bir API ile sunar.
List, Set ve Map en cok kullanilan koleksiyonlardir.
ArrayList siralama korur ve index ile erisim saglar, LinkedList ise ekleme-cikarma islemlerinde daha verimlidir.
HashSet tekrarsiz eleman garantisi verirken, TreeSet elemanlari sirali tutar.
HashMap anahtar-deger cifti saklar ve O(1) erisim suresi sunar.
LinkedHashMap ekleme sirasini korurken, TreeMap anahtarlara gore sirali erisim saglar.
Veri uzerinde filtreleme, donusum ve gruplama gibi islemler Stream API ile okunur sekilde yazilabilir.

## Stream API

Stream API, koleksiyonlar uzerinde fonksiyonel islemleri zincirleme seklinde uygulamak icin kullanilir.
filter() ile kosul bazli eleme, map() ile donusum, collect() ile sonuc toplama yapilir.
reduce() metodu elemanlari tek bir degere indirgemek icin kullanilir.
Intermediate islemler (filter, map, sorted) lazy calisir, terminal islemler (collect, forEach, count) pipeline'i tetikler.
parallelStream() ile cok cekirdekli islemcilerde performans artisi saglanabilir ancak thread safety dikkate alinmalidir.

## Record Siniflar

Java 16 ile gelen record siniflar, degismez (immutable) veri tasiyicilari olusturmak icin kullanilir.
Otomatik olarak constructor, getter, equals, hashCode ve toString metotlari uretilir.
DTO (Data Transfer Object) senaryolarinda sinif tanimini onemli olcude kisaltir.
Compact constructor ile alan dogrulamasi yapilabilir.
Record siniflar Spring AI'da structured output donusumleri icin yogun kullanilir.

## Optional

Optional sinifi, null deger kontrolunu daha guvenli ve okunabilir hale getirir.
Optional.of() kesin deger varsa, Optional.ofNullable() null olabilecek degerler icin kullanilir.
orElse() varsayilan deger, orElseThrow() ise hata firlatma senaryolarinda tercih edilir.
map() ve flatMap() ile Optional icindeki degere donusum uygulanabilir.
Null kontrolu icin if-else yerine Optional kullanmak NullPointerException riskini azaltir.

## Generics

Generics, tip guvenligini derleme zamaninda saglayarak ClassCastException hatalarini onler.
List<String> gibi tanimlamalar koleksiyonun icerigi hakkinda derleme zamani garantisi verir.
Sinif seviyesinde `<T>`, metot seviyesinde `<T> T metot(T param)` seklinde tanimlanir.
Wildcards ile ust sinir (extends) ve alt sinir (super) belirlenebilir.
Spring AI'da BeanOutputConverter<T> gibi generic siniflar tip donusumlerinde kullanilir.

## Hata Yonetimi

Try-catch bloklari beklenen hatalari kontrollu sekilde ele almak icin kullanilir.
Beklenmeyen hatalarda loglama ve anlamli mesaj uretilmesi kritik oneme sahiptir.
Hatanin sessizce yutulmasi, uretim ortaminda tespiti zor problemlere yol acar.
Checked exception derleme zamaninda kontrol edilirken, unchecked exception calisma zamaninda olusur.
Try-with-resources blogu otomatik kaynak kapatmayi garantiler ve kaynak sizintisini onler.
Custom exception siniflar is mantigi hatalarini anlamli sekilde modellemek icin olusturulur.

## Annotation Tabanli Programlama

Java annotation'lari metadata eklemek icin kullanilir ve framework'ler tarafindan calisma zamaninda okunur.
@Override metot ezmede derleme zamani kontrolu saglar.
@Deprecated kullanimdan kaldirilan elemanlari isaretler.
Spring ekosisteminde annotation tabanli programlama modeli yogun kullanilir.
@RestController, @Service, @Configuration gibi annotation'lar katmanlari netlestirir ve bagimlilik yonetimini sadelestirir.
@Autowired ve constructor injection ile bagimliliklar otomatik olarak cozumlenir.

## Spring Boot ile Birlesim

Spring Boot, convention-over-configuration prensibiyle hizli uygulama gelistirme sunar.
Auto-configuration mekanizmasi classpath'teki kutuphanelere gore otomatik yapilandirma yapar.
application.yml veya application.properties ile dis konfigürasyon yonetilir.
Profile mekanizmasi farkli ortamlar (dev, test, prod) icin ayri yapilandirma saglar.
Starter dependency'ler ilgili tum kutuphaneleri tek bir bagimlilikla projeye ekler.
Actuator modulu saglik kontrolu, metrik ve izleme endpoint'leri sunar.

Bu dokuman B9 derslerinde Markdown tabanli reader akislarini gostermek icin kullanilacaktir.
Icerikteki konu cesitliligi, retrieval sorgularinda farkli alanlarda arama yapilabilmesini saglar.
