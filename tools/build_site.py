#!/usr/bin/env python3
"""Builds index.html, <lang>/index.html and sitemap.xml from tools/index.template.html.
Run from the repo root: python3 tools/build_site.py"""
import re, datetime, pathlib
BASE = "https://therealwiz007.github.io/Wiz-Snap-Notes/"
PLAY = "https://play.google.com/store/apps/details?id=com.wizlabs.snapnotes&amp;referrer=utm_source%3Dwizlabs_site%26utm_medium%3Dlanding%26utm_campaign%3DWiz-Snap-Notes"
# code: (folder, html lang, og locale, native name, dir)
L = {"en": ("", "en", "en_US", "English"),
     "es": ("es/", "es", "es_ES", "Español"), "pt-BR": ("pt-br/", "pt-BR", "pt_BR", "Português"),
     "fr": ("fr/", "fr", "fr_FR", "Français"), "de": ("de/", "de", "de_DE", "Deutsch"),
     "id": ("id/", "id", "id_ID", "Bahasa Indonesia"), "ar": ("ar/", "ar", "ar_AR", "العربية"),
     "ru": ("ru/", "ru", "ru_RU", "Русский"), "hi": ("hi/", "hi", "hi_IN", "हिंदी")}
# title, description, h1, pitch, uses heading, uses, features heading, features, privacy label, disclaimer
S = {
"es": ("Wiz Snap Notes: Notas con Foto para Viajes, Compras y Trabajo",
 "Guarda recuerdos con foto: encabezado, fecha, hora, lugar y notas impresos en la imagen, con búsqueda instantánea.",
 "Wiz Snap Notes: tu memoria visual",
 "Míralo. Fotografíalo. Recuérdalo. Guarda un viaje, una tienda, un centro comercial, dónde aparcaste o una visita de trabajo con una sola foto.",
 "Úsala para", ["Viajes: lugares, comidas, hoteles y ideas a las que quieres volver.", "Compras y centros comerciales: productos, precios y tiendas que te gustaron.", "Dónde aparcaste y todo lo que no quieras olvidar.", "Visitas de obra, inspecciones y auditorías con prueba de fecha y hora.", "Mantenimiento, reparaciones y obras con fotos de antes y después."],
 "Qué hace", ["Encabezado, fecha y hora impresos arriba de la foto y tus notas abajo.", "Varias fotos bajo un encabezado y un solo correo con todas.", "Sello de ubicación opcional: nombre del lugar y GPS en la foto.", "Historial con búsqueda y cronología.", "Copia opcional en tu Google Drive. Sin anuncios ni rastreo."],
 "Política de privacidad", "Wiz Snap Notes es una app independiente de Wiz Labs. No está afiliada ni respaldada por ninguna empresa mencionada o implícita en sus casos de uso."),
"pt-BR": ("Wiz Snap Notes: Notas com Foto para Viagens, Compras e Trabalho",
 "Guarde lembranças com foto: título, data, hora, local e notas impressos na imagem, com busca instantânea.",
 "Wiz Snap Notes: sua memória visual",
 "Veja. Fotografe. Lembre. Guarde uma viagem, uma loja, um shopping, onde estacionou ou uma visita de trabalho com uma só foto.",
 "Use para", ["Viagens: lugares, comidas, hotéis e ideias para rever.", "Compras e shoppings: produtos, preços e lojas que você gostou.", "Onde você estacionou e tudo que não quer esquecer.", "Visitas técnicas, inspeções e auditorias com prova de data e hora.", "Manutenção, reparos e obras com fotos de antes e depois."],
 "O que faz", ["Título, data e hora impressos no topo da foto e suas notas embaixo.", "Várias fotos sob um título e um só e-mail com todas.", "Carimbo de local opcional: nome do lugar e GPS na foto.", "Histórico pesquisável com linha do tempo.", "Backup opcional no seu Google Drive. Sem anúncios nem rastreamento."],
 "Política de privacidade", "O Wiz Snap Notes é um app independente da Wiz Labs. Não é afiliado nem endossado por nenhuma empresa citada ou subentendida nos casos de uso."),
"fr": ("Wiz Snap Notes : notes photo pour voyages, achats et travail",
 "Gardez vos souvenirs en photo : titre, date, heure, lieu et notes imprimés sur l'image, avec recherche instantanée.",
 "Wiz Snap Notes : votre mémoire visuelle",
 "Voyez. Photographiez. Retenez. Gardez un voyage, une boutique, un centre commercial, où vous êtes garé ou une visite de travail en une seule photo.",
 "Utilisez-la pour", ["Voyages : lieux, plats, hôtels et idées où revenir.", "Achats et centres commerciaux : produits, prix et boutiques qui vous ont plu.", "Où vous êtes garé et tout ce que vous ne voulez pas oublier.", "Visites de site, inspections et audits avec preuve datée.", "Maintenance, réparations et chantiers avec photos avant et après."],
 "Ce qu'elle fait", ["Titre, date et heure en haut de la photo, vos notes en bas.", "Plusieurs photos sous un titre et un seul e-mail avec toutes.", "Tampon de lieu facultatif : nom du lieu et GPS sur la photo.", "Historique consultable avec chronologie.", "Sauvegarde facultative dans votre Google Drive. Sans publicité ni suivi."],
 "Politique de confidentialité", "Wiz Snap Notes est une app indépendante de Wiz Labs. Elle n'est affiliée à aucune entreprise citée ou sous-entendue dans ses cas d'usage, ni approuvée par elle."),
"de": ("Wiz Snap Notes: Foto-Notizen für Reisen, Einkauf und Arbeit",
 "Erinnerungen als Foto festhalten: Überschrift, Datum, Uhrzeit, Ort und Notizen direkt im Bild, mit Sofortsuche.",
 "Wiz Snap Notes: dein visuelles Gedächtnis",
 "Sehen. Knipsen. Merken. Halte eine Reise, einen Laden, ein Einkaufszentrum, deinen Parkplatz oder einen Arbeitstermin mit einem Foto fest.",
 "Einsatzzwecke", ["Reisen: Orte, Essen, Hotels und Ideen, zu denen du zurückwillst.", "Einkaufen und Shopping-Center: Produkte, Preise und Läden, die dir gefielen.", "Wo du geparkt hast und alles, was du nicht vergessen willst.", "Ortstermine, Inspektionen und Audits mit Zeitnachweis.", "Wartung, Reparaturen und Bau mit Vorher-Nachher-Fotos."],
 "Funktionen", ["Überschrift, Datum und Uhrzeit oben im Foto, deine Notizen unten.", "Mehrere Fotos unter einer Überschrift, eine E-Mail mit allen.", "Optionaler Standortstempel: Ortsname und GPS im Foto.", "Durchsuchbarer Verlauf mit Zeitleiste.", "Optionale Sicherung in dein Google Drive. Keine Werbung, kein Tracking."],
 "Datenschutzerklärung", "Wiz Snap Notes ist eine unabhängige App von Wiz Labs. Sie ist weder mit Unternehmen verbunden, die in den Einsatzbeispielen genannt oder gemeint sind, noch von ihnen empfohlen."),
"id": ("Wiz Snap Notes: Catatan Foto untuk Perjalanan, Belanja, dan Kerja",
 "Simpan kenangan lewat foto: judul, tanggal, jam, lokasi, dan catatan tercetak di gambar, dengan pencarian instan.",
 "Wiz Snap Notes: memori visual Anda",
 "Lihat. Foto. Ingat. Simpan perjalanan, toko, mal, tempat parkir, atau kunjungan kerja dengan satu foto.",
 "Gunakan untuk", ["Perjalanan: tempat, makanan, hotel, dan ide untuk dikunjungi lagi.", "Belanja dan mal: produk, harga, dan toko yang Anda suka.", "Tempat parkir dan apa pun yang tidak ingin Anda lupakan.", "Kunjungan lapangan, inspeksi, dan audit dengan bukti waktu.", "Perawatan, perbaikan, dan konstruksi dengan foto sebelum dan sesudah."],
 "Fitur", ["Judul, tanggal, dan jam tercetak di atas foto, catatan Anda di bawah.", "Beberapa foto di bawah satu judul dan satu email berisi semuanya.", "Cap lokasi opsional: nama tempat dan GPS di foto.", "Riwayat yang bisa dicari dengan linimasa.", "Cadangan opsional ke Google Drive Anda. Tanpa iklan dan pelacakan."],
 "Kebijakan privasi", "Wiz Snap Notes adalah aplikasi independen dari Wiz Labs. Aplikasi ini tidak berafiliasi dengan atau didukung oleh perusahaan mana pun yang disebut atau tersirat dalam contoh penggunaannya."),
"ar": ("Wiz Snap Notes: ملاحظات بالصور للسفر والتسوق والعمل",
 "احفظ ذكرياتك بالصور: عنوان وتاريخ ووقت ومكان وملاحظات مطبوعة على الصورة، مع بحث فوري.",
 "Wiz Snap Notes: ذاكرتك البصرية",
 "شاهد. صوّر. تذكّر. احفظ رحلة أو متجراً أو مركز تسوق أو مكان ركن سيارتك أو زيارة عمل بصورة واحدة.",
 "استخدمه في", ["السفر: أماكن وطعام وفنادق وأفكار تريد العودة إليها.", "التسوق والمولات: منتجات وأسعار ومتاجر أعجبتك.", "مكان ركن سيارتك وكل ما لا تريد نسيانه.", "زيارات المواقع والتفتيش والتدقيق مع إثبات بالتاريخ والوقت.", "الصيانة والإصلاح والإنشاءات مع صور قبل وبعد."],
 "ماذا يفعل", ["عنوان وتاريخ ووقت في أعلى الصورة وملاحظاتك في أسفلها.", "عدة صور تحت عنوان واحد وبريد واحد يضمها كلها.", "ختم موقع اختياري: اسم المكان وGPS على الصورة.", "سجل قابل للبحث مع خط زمني.", "نسخ احتياطي اختياري إلى Google Drive الخاص بك. بلا إعلانات ولا تتبّع."],
 "سياسة الخصوصية", "Wiz Snap Notes تطبيق مستقل من Wiz Labs. وهو غير تابع ولا معتمد من أي شركة مذكورة أو ملمَّح إليها في أمثلة الاستخدام."),
"ru": ("Wiz Snap Notes: фото-заметки для поездок, покупок и работы",
 "Сохраняйте воспоминания фото: заголовок, дата, время, место и заметки на снимке, с мгновенным поиском.",
 "Wiz Snap Notes: ваша визуальная память",
 "Увидел. Снял. Запомнил. Сохраните поездку, магазин, торговый центр, место парковки или рабочий визит одним фото.",
 "Для чего подходит", ["Поездки: места, еда, отели и идеи, куда хочется вернуться.", "Покупки и торговые центры: товары, цены и магазины, которые понравились.", "Где вы припарковались и всё, что не хочется забыть.", "Выезды на объекты, инспекции и аудиты с подтверждением даты и времени.", "Обслуживание, ремонт и стройка с фото до и после."],
 "Что умеет", ["Заголовок, дата и время вверху фото, ваши заметки внизу.", "Несколько фото под одним заголовком и одно письмо со всеми.", "Необязательный штамп места: название и GPS на фото.", "История с поиском и лентой по дням.", "Необязательная копия в ваш Google Drive. Без рекламы и слежки."],
 "Политика конфиденциальности", "Wiz Snap Notes — независимое приложение Wiz Labs. Оно не связано ни с одной компанией, названной или подразумеваемой в примерах использования, и не одобрено ими."),
"hi": ("Wiz Snap Notes: यात्रा, खरीदारी और काम के लिए फोटो नोट्स",
 "यादें फोटो में सहेजें: हेडर, तारीख, समय, जगह और नोट्स सीधे तस्वीर पर, तुरंत सर्च के साथ।",
 "Wiz Snap Notes: आपकी विज़ुअल मेमोरी",
 "देखो। फोटो लो। याद रखो। कोई यात्रा, दुकान, मॉल, पार्किंग की जगह या काम की विज़िट, एक फोटो में सहेजें।",
 "इनके लिए इस्तेमाल करें", ["यात्रा: जगहें, खाना, होटल और वे आइडिया जहाँ दोबारा जाना है।", "खरीदारी और मॉल: पसंद आए प्रोडक्ट, दाम और दुकानें।", "गाड़ी कहाँ पार्क की और वह सब जो भूलना नहीं चाहते।", "साइट विज़िट, इंस्पेक्शन और ऑडिट, तारीख-समय के सबूत के साथ।", "मेंटेनेंस, मरम्मत और निर्माण, पहले और बाद की फोटो के साथ।"],
 "क्या करता है", ["फोटो के ऊपर हेडर, तारीख और समय, नीचे आपके नोट्स।", "एक हेडर के नीचे कई फोटो और सबके साथ एक ही ईमेल।", "वैकल्पिक लोकेशन स्टैम्प: जगह का नाम और GPS फोटो पर।", "सर्च योग्य हिस्ट्री और टाइमलाइन।", "अपने Google Drive में वैकल्पिक बैकअप। कोई विज्ञापन या ट्रैकिंग नहीं।"],
 "गोपनीयता नीति", "Wiz Snap Notes, Wiz Labs का स्वतंत्र ऐप है। यह उपयोग के उदाहरणों में बताई या सुझाई गई किसी भी कंपनी से जुड़ा या उनके द्वारा समर्थित नहीं है।"),
}
root = pathlib.Path(__file__).resolve().parent.parent
tpl = (root / "tools/index.template.html").read_text(encoding="utf8")
style = re.search(r"<style>.*?</style>", tpl, re.S).group(0)
body_play = re.search(r'<a class="play".*?</a>', tpl, re.S).group(0)
shots = re.search(r'<div class="shots">.*?</div>', tpl, re.S).group(0)
today = datetime.date.today().isoformat()

def url(c): return BASE + L[c][0]
def hreflangs():
    out = [f'<link rel="alternate" hreflang="{L[c][1]}" href="{url(c)}">' for c in L]
    return "\n".join(out + [f'<link rel="alternate" hreflang="x-default" href="{BASE}">'])
def picker(c, prefix):
    parts = []
    for k, v in L.items():
        parts.append(f'<a href="{prefix}{v[0]}" hreflang="{v[1]}" lang="{v[1]}">{v[3]}</a>' if k != c else f'<b>{v[3]}</b>')
    return " · ".join(parts)
def li(x): return "\n".join(f"    <li>{i}</li>" for i in x)

def rel(c): return "../" if L[c][0] else ""

# English
en = tpl.replace("<!--HREFLANG-->", hreflangs()).replace('<p class="langs">LANGS</p>', f'<p class="langs">{picker("en", "")}</p>')
(root / "index.html").write_text(en, encoding="utf8")

for c, (t, d, h1, pitch, uh, uses, fh, feats, priv, disc) in S.items():
    r = rel(c)
    fix = lambda x: x.replace('src="site/', f'src="{r}site/').replace('href="site/', f'href="{r}site/')
    dirattr = ' dir="rtl"' if c == "ar" else ""
    page = f'''<!doctype html>
<html lang="{L[c][1]}"{dirattr}>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>{t}</title>
<meta name="description" content="{d}">
<link rel="canonical" href="{url(c)}">
{hreflangs()}
<link rel="icon" href="{r}site/icon-96.png">
<meta name="theme-color" content="#FFC400">
<meta property="og:type" content="website">
<meta property="og:title" content="{t}">
<meta property="og:description" content="{d}">
<meta property="og:url" content="{url(c)}">
<meta property="og:image" content="{BASE}site/og-image.png">
<meta property="og:locale" content="{L[c][2]}">
<meta property="og:site_name" content="Wiz Labs">
{style}
</head>
<body>
<main>
<header>
  <img src="{r}site/icon-96.png" alt="Wiz Snap Notes" width="56" height="56">
  <h1>{h1}</h1>
</header>
<p class="pitch">{pitch}</p>
{body_play}
<p class="langs">{picker(c, r)}</p>
{fix(shots)}
<section>
  <h2>{uh}</h2>
  <ul>
{li(uses)}
  </ul>
</section>
<section>
  <h2>{fh}</h2>
  <ul>
{li(feats)}
  </ul>
</section>
</main>
<footer>
  <p><a href="{r}privacy-policy.html">{priv}</a> · <a href="mailto:blazewizofficial@gmail.com">blazewizofficial@gmail.com</a></p>
  <p>{disc} Google Play is a trademark of Google LLC.</p>
</footer>
</body>
</html>
'''
    out = root / L[c][0] / "index.html"
    out.parent.mkdir(exist_ok=True)
    out.write_text(page, encoding="utf8")

alts = "\n".join(f'    <xhtml:link rel="alternate" hreflang="{L[c][1]}" href="{url(c)}"/>' for c in L) + f'\n    <xhtml:link rel="alternate" hreflang="x-default" href="{BASE}"/>'
urls = "".join(f"  <url>\n    <loc>{url(c)}</loc>\n    <lastmod>{today}</lastmod>\n{alts}\n  </url>\n" for c in L)
urls += f"  <url>\n    <loc>{BASE}privacy-policy.html</loc>\n    <lastmod>{today}</lastmod>\n  </url>\n"
(root / "sitemap.xml").write_text('<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:xhtml="http://www.w3.org/1999/xhtml">\n' + urls + "</urlset>\n", encoding="utf8")
print("built", len(L), "pages")
