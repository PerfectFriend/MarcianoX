package com.example.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppLanguage(val code: String, val label: String) {
    ENGLISH("en", "English"),
    RUSSIAN("ru", "Русский"),
    SPANISH("es", "Español"),
    CHINESE("zh", "简体中文"),
    HINDI("hi", "हिन्दी"),
    ARABIC("ar", "العربية"),
    PORTUGUESE("pt", "Português"),
    FRENCH("fr", "Français")
}

object LocalizationHelper {
    private val _currentLanguage = MutableStateFlow(AppLanguage.ENGLISH)
    val currentLanguage: StateFlow<AppLanguage> = _currentLanguage.asStateFlow()

    fun setLanguage(language: AppLanguage) {
        _currentLanguage.value = language
    }

    private val translations = mapOf(
        "app_name" to mapOf(
            "en" to "MarcianoX",
            "ru" to "MarcianoX",
            "es" to "MarcianoX",
            "zh" to "MarcianoX",
            "hi" to "MarcianoX",
            "ar" to "MarcianoX",
            "pt" to "MarcianoX",
            "fr" to "MarcianoX"
        ),
        "tor_routing" to mapOf(
            "en" to "TOR ROUTING",
            "ru" to "ТОР МАРШРУТЫ",
            "es" to "RUTA TOR",
            "zh" to "洋葱路由",
            "hi" to "टोर रूटिंग",
            "ar" to "توجيه تور",
            "pt" to "ROTEAMENTO TOR",
            "fr" to "ROUTAGE TOR"
        ),
        "decoy_systems" to mapOf(
            "en" to "DECOY SYSTEMS",
            "ru" to "ПРИМАНКИ",
            "es" to "SISTEMAS SEÑUELO",
            "zh" to "诱饵防御",
            "hi" to "डीकॉय सिस्टम",
            "ar" to "أنظمة التمويه",
            "pt" to "SISTEMAS ISCA",
            "fr" to "SYSTÈMES LEURRES"
        ),
        "chain_tunnel" to mapOf(
            "en" to "CHAIN TUNNEL",
            "ru" to "ЦЕПОЧКА МОСТОВ",
            "es" to "TÚNEL EN CADENA",
            "zh" to "链式代理隧道",
            "hi" to "चेन टनल",
            "ar" to "سلسلة الجسور",
            "pt" to "TÚNEL EM CADEIA",
            "fr" to "TUNNEL EN CHAÎNE"
        ),
        "active_profile" to mapOf(
            "en" to "CURRENT SYSTEM HANDSHAKE PROFILE",
            "ru" to "АКТИВНЫЙ ПРОФИЛЬ ТРАФИКА",
            "es" to "PERFIL DE INTEGRIDAD DEL SISTEMA",
            "zh" to "当前系统安全配置文件",
            "hi" to "सक्रिय सिस्टम प्रोफ़ाइल",
            "ar" to "ملف تعريف أمان النظام الحالي",
            "pt" to "PERFIL DE INTEGRIDADE DO SISTEMA",
            "fr" to "PROFIL DE SÉCURITÉ DU SYSTÈME"
        ),
        "heap_audit" to mapOf(
            "en" to "HEAP MEMORY AUDIT & ZEROIZER",
            "ru" to "ОЧИСТКА И ОБНУЛЕНИЕ ОЗУ",
            "es" to "AUDITORÍA Y ZEROIZADOR DE MEMORIA",
            "zh" to "堆内存审计与归零清除",
            "hi" to "मेमोरी ऑडिट और ज़ीरोइज़र",
            "ar" to "فحص وتصفير ذاكرة الوصول العشوائي",
            "pt" to "AUDITORIA E ZEROIZADOR DE MEMÓRIA",
            "fr" to "AUDIT & REMISE À ZÉRO DE LA MÉMOIRE"
        ),
        "duress_disclaimer" to mapOf(
            "en" to "DURESS PROTOCOLS: Entering your registered Distress PIN on the lockscreen bypasses real databases and loads simulated sandbox profiles. Entering your registered Panic PIN instantly zeroizes master encryption keys and shreds database tables.",
            "ru" to "ПРОТОКОЛЫ ПОД ПРИНУЖДЕНИЕМ: Ввод тревожного PIN-кода на экране блокировки загружает фейковый профиль. Ввод экстренного Panic PIN-кода мгновенно стирает все ключи шифрования и уничтожает базы данных.",
            "es" to "PROTOCOLOS DE COACCIÓN: Ingresar su PIN de Alerta en la pantalla de bloqueo carga un perfil falso. El PIN de Pánico destruye instantáneamente las claves de cifrado y los datos.",
            "zh" to "胁迫协议：在锁定屏幕上输入注册的‘警告PIN码’将加载模拟沙盒配置文件。输入‘恐慌PIN码’将瞬间销毁主密钥并清除所有数据库。",
            "hi" to "दबाव प्रोटोकॉल: लॉकस्क्रीन पर डिस्ट्रेस पिन डालने से फर्जी प्रोफाइल लोड होती है। पैनिक पिन डालने से मास्टर एन्क्रिप्शन कीज़ तुरंत नष्ट हो जाती हैं।",
            "ar" to "بروتوكولات الإكراه: يؤدي إدخال رمز PIN للطوارئ على شاشة القفل إلى تحميل ملف تعريف مموه. يؤدي إدخال رمز PIN للذعر إلى تدمير مفاتيح التشفير وقاعدة البيانات فورًا.",
            "pt" to "PROTOCOLOS sob COAÇÃO: Digitar o PIN de Alerta na tela de bloqueio carrega um perfil falso. O PIN de Pânico destrói instantaneamente as chaves de criptografia e os bancos de dados.",
            "fr" to "PROTOCOLES SOUS CONTRAINTE: Saisir votre code PIN de Détresse sur l'écran de verrouillage charge un faux profil. Saisir votre code PIN de Panique détruit instantanément les clés de chiffrement."
        ),
        "connect_chain" to mapOf(
            "en" to "CONNECT CHAIN",
            "ru" to "ЗАПУСТИТЬ ЦЕПОЧКУ",
            "es" to "CONECTAR CADENA",
            "zh" to "启动隧道链",
            "hi" to "चेन कनेक्ट करें",
            "ar" to "ربط السلسلة",
            "pt" to "CONECTAR CADEIA",
            "fr" to "LANCER LA CHAÎNE"
        ),
        "disconnect_chain" to mapOf(
            "en" to "DISCONNECT CHAIN",
            "ru" to "РАЗОРВАТЬ ЦЕПОЧКУ",
            "es" to "DESCONECTAR CADENA",
            "zh" to "断开隧道链",
            "hi" to "चेन डिस्कनेक्ट करें",
            "ar" to "قطع السلسلة",
            "pt" to "DESCONECTAR CADEIA",
            "fr" to "COUPER LA CHAÎNE"
        ),
        "socks_logs" to mapOf(
            "en" to "REAL-TIME SOCKS5 LOGS",
            "ru" to "ЛОГИ SOCKS5 В РЕАЛЬНОМ ВРЕМЕНИ",
            "es" to "REGISTROS SOCKS5 EN TIEMPO REAL",
            "zh" to "实时 SOCKS5 网络日志",
            "hi" to "वास्तविक समय SOCKS5 लॉग",
            "ar" to "سجلات SOCKS5 المباشرة",
            "pt" to "REGISTROS SOCKS5 EM TEMPO REAL",
            "fr" to "LOGS SOCKS5 EN TEMPS RÉEL"
        ),
        "socks_daemon" to mapOf(
            "en" to "LOCAL SOCKS5 DAEMON",
            "ru" to "ЛОКАЛЬНЫЙ ДЕМОН SOCKS5",
            "es" to "DEMONIO SOCKS5 LOCAL",
            "zh" to "本地 SOCKS5 守护进程",
            "hi" to "स्थानीय SOCKS5 डेमन",
            "ar" to "خادم SOCKS5 المحلي",
            "pt" to "DEMONIO SOCKS5 LOCAL",
            "fr" to "DÉMON SOCKS5 LOCAL"
        ),
        "route_tester" to mapOf(
            "en" to "ROUTE TESTER (PROBE ONION ENDPOINT)",
            "ru" to "ТЕСТЕР МАРШРУТА (ПИНГ .ONION АДРЕСА)",
            "es" to "PROBADOR DE RUTA (.ONION)",
            "zh" to "路由测试器 (探测 .onion 节点)",
            "hi" to "रूट परीक्षक (.onion)",
            "ar" to "مختبر المسار (فحص عنوان .onion)",
            "pt" to "PROBADOR DE ROTA (.ONION)",
            "fr" to "TESTEUR DE ROUTE (.ONION)"
        ),
        "close_center" to mapOf(
            "en" to "CLOSE CENTER",
            "ru" to "ЗАКРЫТЬ ЦЕНТР",
            "es" to "CERRAR CONFIGURACIÓN",
            "zh" to "关闭控制中心",
            "hi" to "बंद करें",
            "ar" to "إغلاق المركز",
            "pt" to "FECHAR CENTRAL",
            "fr" to "FERMER LE CENTRE"
        ),
        "add_custom" to mapOf(
            "en" to "ADD CUSTOM NODE",
            "ru" to "ДОБАВИТЬ НОДУ",
            "es" to "AÑADIR NODO",
            "zh" to "添加自定义节点",
            "hi" to "नया नोड जोड़ें",
            "ar" to "إضافة عقدة مخصصة",
            "pt" to "ADICIONAR NODO",
            "fr" to "AJOUTER UN NŒUD"
        ),
        "burn_timeout_label" to mapOf(
            "en" to "Undelivered Burn Timeout (N):",
            "ru" to "Автосжигание недоставленных (N):",
            "es" to "Autodestrucción de no entregados (N):",
            "zh" to "未送达消息自动烧毁时限 (N):",
            "hi" to "अवितरित संदेश नष्ट करने का समय (N):",
            "ar" to "مؤقت حرق الرسائل غير المستلمة (N):",
            "pt" to "Autodestruição de não entregues (N):",
            "fr" to "Autodestruction si non distribué (N):"
        ),
        "onion_proxy_label" to mapOf(
            "en" to "Onion Link Proxy Mode:",
            "ru" to "Прокси-режим Onion сети:",
            "es" to "Modo de proxy Onion:",
            "zh" to "洋葱链路代理模式:",
            "hi" to "ऑनियन प्रॉक्सी मोड:",
            "ar" to "وضع وكيل رابط البصلة:",
            "pt" to "Modo Proxy Onion:",
            "fr" to "Mode Proxy Onion:"
        ),
        "time_warp_label" to mapOf(
            "en" to "Onion Queue Time Warp (Testing):",
            "ru" to "Временной варп очереди (Тесты):",
            "es" to "Acelerar tiempo de cola (Pruebas):",
            "zh" to "洋葱发送队列时间跃迁 (测试):",
            "hi" to "समय躍遷 (परीक्षण):",
            "ar" to "تسريع وقت طابور الانتظار (تجريبي):",
            "pt" to "Acelerar tempo de fila (Testes):",
            "fr" to "Accélérer temps de file (Tests):"
        ),
        "import_config" to mapOf(
            "en" to "IMPORT CONFIG",
            "ru" to "ИМПОРТИРОВАТЬ КОНФИГ",
            "es" to "IMPORTAR CONFIG",
            "zh" to "导入配置文件",
            "hi" to "कॉन्फ़िगरेशन आयात करें",
            "ar" to "استيراد التكوين",
            "pt" to "IMPORTAR CONFIG",
            "fr" to "IMPORTER CONFIG"
        ),
        "select_working_account" to mapOf(
            "en" to "SELECT WORKING IDENTITY PROFILE",
            "ru" to "ВЫБЕРИТЕ РАБОЧИЙ ПРОФИЛЬ",
            "es" to "SELECCIONAR PERFIL DE IDENTIDAD",
            "zh" to "选择工作身份配置文件",
            "hi" to "कार्यशील पहचान प्रोफ़ाइल चुनें",
            "ar" to "حدد ملف تعريف الهوية",
            "pt" to "SELECIONAR PERFIL DE IDENTIDADE",
            "fr" to "SÉLECTIONNER UN PROFIL D'IDENTITÉ"
        ),
        "auto_lock" to mapOf(
            "en" to "AUTO-LOCK TIMER",
            "ru" to "АВТО-БЛОКИРОВКА",
            "es" to "AUTO-BLOQUEO",
            "zh" to "自动锁屏定时器",
            "hi" to "ऑटो-लॉक टाइमर",
            "ar" to "مؤقت القفل التلقائي",
            "pt" to "TIMER DE AUTO-BLOQUEIO",
            "fr" to "VERROUILLAGE AUTO"
        ),
        "auto_lock_title" to mapOf(
            "en" to "AUTOMATIC APP-LOCK TIMER",
            "ru" to "ТАЙМЕР АВТОБЛОКИРОВКИ",
            "es" to "TEMPORIZADOR DE AUTO-BLOQUEO",
            "zh" to "应用自动锁定定时器",
            "hi" to "स्वचालित ऐप-लॉक टाइमर",
            "ar" to "مؤقت قفل التطبيق التلقائي",
            "pt" to "TEMPORIZADOR DE BLOQUEIO",
            "fr" to "MINUTEUR DE VERROUILLAGE AUTO"
        ),
        "auto_lock_desc" to mapOf(
            "en" to "Controls how long the app stays unlocked during user inactivity before automatically securing all channels and requiring the 6-digit master PIN again. All in-memory cryptographic keys are zeroized upon lock.",
            "ru" to "Определяет, как долго приложение остаётся разблокированным при бездействии, прежде чем автоматически заблокировать каналы и снова запросить 6-значный PIN. Все ключи в ОЗУ обнуляются при блокировке.",
            "es" to "Controla cuánto tiempo permanece desbloqueada la aplicación durante la inactividad antes de exigir el PIN de 6 dígitos nuevamente y borrar las claves en memoria.",
            "zh" to "控制应用在用户无操作时保持解锁的时间。超时后将自动锁定通道，清零内存密钥，并重新要求输入6位PIN码。",
            "hi" to "यह नियंत्रित करता है कि निष्क्रियता के दौरान ऐप कितने समय तक अनलॉक रहता है। समय समाप्त होने पर 6-अंकीय पिन आवश्यक होगा और कुंजियाँ मिटा दी जाएँगी।",
            "ar" to "يتحكم في مدة بقاء التطبيق مفتوحًا أثناء عدم النشاط قبل قفل القنوات تلقائيًا وطلب رمز PIN المكون من 6 أرقام وتصفير المفاتيح في الذاكرة.",
            "pt" to "Controla por quanto tempo o app fica desbloqueado durante inatividade antes de bloquear os canais, zerar as chaves na memória e exigir o PIN de 6 dígitos novamente.",
            "fr" to "Contrôle la durée pendant laquelle l'application reste déverrouillée en cas d'inactivité avant de verrouiller les canaux, d'effacer les clés en mémoire et d'exiger le code PIN à 6 chiffres."
        ),
        "auto_lock_countdown_label" to mapOf(
            "en" to "COUNTDOWN UNTIL AUTO-LOCK:",
            "ru" to "ДО АВТОБЛОКИРОВКИ:",
            "es" to "CUENTA REGRESIVA:",
            "zh" to "自动锁定倒计时:",
            "hi" to "ऑटो-लॉक उलटी गिनती:",
            "ar" to "العد التنازلي للقفل:",
            "pt" to "CONTAGEM REGRESSIVA:",
            "fr" to "COMPTE À REBOURS :"
        ),
        "auto_lock_remaining_suffix" to mapOf(
            "en" to "remaining",
            "ru" to "осталось",
            "es" to "restantes",
            "zh" to "剩余",
            "hi" to "शेष",
            "ar" to "متبقي",
            "pt" to "restantes",
            "fr" to "restantes"
        ),
        "auto_lock_presets_header" to mapOf(
            "en" to "CONFIGURED TIMEOUT PRESETS",
            "ru" to "ПРЕДУСТАНОВЛЕННЫЕ ИНТЕРВАЛЫ",
            "es" to "INTERVALOS PREDEFINIDOS",
            "zh" to "预设超时时间",
            "hi" to "पूर्व-निर्धारित समय सीमा",
            "ar" to "فترات المهلة المعدة مسبقًا",
            "pt" to "PREDEFINIÇÕES DE TEMPO",
            "fr" to "PRÉRÉGLAGES DE DÉLAI"
        ),
        "auto_lock_custom_slider" to mapOf(
            "en" to "CUSTOM SECONDS SLIDER:",
            "ru" to "ТОЧНАЯ НАСТРОЙКА (СЕК):",
            "es" to "AJUSTE PERSONALIZADO (SEG):",
            "zh" to "自定义秒数滑块:",
            "hi" to "कस्टम सेकंड स्लाइडर:",
            "ar" to "شريط التمرير المخصص (ثانية):",
            "pt" to "AJUSTE PERSONALIZADO (SEG):",
            "fr" to "CURSEUR PERSONNALISÉ (SEC) :"
        ),
        "auto_lock_telemetry_title" to mapOf(
            "en" to "ZEROIZATION & MEMORY PURGE TELEMETRY",
            "ru" to "ТЕЛЕМЕТРИЯ ОБНУЛЕНИЯ ОЗУ",
            "es" to "TELEMETRÍA DE BORRADO DE MEMORIA",
            "zh" to "内存清零与净化遥测",
            "hi" to "मेमोरी पर्च और ज़ीरोइज़ेशन टेलीमेट्री",
            "ar" to "قياس تصفير وتطهير الذاكرة",
            "pt" to "TELEMETRIA DE LIMPEZA DE MEMÓRIA",
            "fr" to "TÉLÉMÉTRIE DE PURGE MÉMOIRE"
        ),
        "auto_lock_telemetry_body" to mapOf(
            "en" to "• On timeout, MemoryZeroizationManager wipes master storage keys from heap\n• OS Screen Capture Guard is enforced on lockscreen\n• Requires 6-digit PIN authentication to reconstruct cryptographic state",
            "ru" to "• По таймауту MemoryZeroizationManager стирает мастер-ключи из heap-памяти\n• На экране блокировки включена защита от снимков экрана (FLAG_SECURE)\n• Для восстановления сессии требуется ввод 6-значного PIN-кода",
            "es" to "• Al expirar, MemoryZeroizationManager borra las claves maestras del heap\n• La protección de captura de pantalla está activa en el bloqueo\n• Requiere autenticación con PIN de 6 dígitos",
            "zh" to "• 超时后 MemoryZeroizationManager 会从堆内存中清除主密钥\n• 锁屏界面强制启用系统防截屏保护 (FLAG_SECURE)\n• 需要输入6位PIN码重建加密状态",
            "hi" to "• समय समाप्त होने पर MemoryZeroizationManager हीप से मास्टर कुंजियाँ मिटा देता है\n• लॉकस्क्रीन पर स्क्रीन कैप्चर सुरक्षा सक्रिय है\n• क्रिप्टोग्राफ़िक स्थिति पुनर्निर्माण हेतु 6-अंकीय पिन आवश्यक है",
            "ar" to "• عند انتهاء المهلة يمسح MemoryZeroizationManager مفاتيح التشفير من الذاكرة\n• حماية منع التقاط الشاشة مفعلة على شاشة القفل\n• يتطلب مصادقة PIN من 6 أرقام لاستعادة الحالة المشفرة",
            "pt" to "• Ao expirar, o MemoryZeroizationManager apaga as chaves mestras do heap\n• Proteção contra captura de tela ativa no bloqueio\n• Requer autenticação por PIN de 6 dígitos",
            "fr" to "• À expiration, MemoryZeroizationManager efface les clés maîtresses du tas\n• La protection contre la capture d'écran est active sur l'écran verrouillé\n• Nécessite un code PIN à 6 chiffres"
        ),
        "auto_lock_now_button" to mapOf(
            "en" to "LOCK APPLICATION NOW",
            "ru" to "ЗАБЛОКИРОВАТЬ СЕЙЧАС",
            "es" to "BLOQUEAR APLICACIÓN AHORA",
            "zh" to "立即锁定应用",
            "hi" to "अभी लॉक करें",
            "ar" to "قفل التطبيق الآن",
            "pt" to "BLOQUEAR APLICATIVO AGORA",
            "fr" to "VERROUILLER MAINTENANT"
        ),
        "auto_lock_manual_badge" to mapOf(
            "en" to "MANUAL",
            "ru" to "РУЧНОЙ",
            "es" to "MANUAL",
            "zh" to "手动",
            "hi" to "मैनुअल",
            "ar" to "يدوي",
            "pt" to "MANUAL",
            "fr" to "MANUEL"
        ),
        "auto_lock_bg_badge" to mapOf(
            "en" to "BG-LOCK",
            "ru" to "ФОН-БЛОК",
            "es" to "BLOQ-2ºP",
            "zh" to "后台锁",
            "hi" to "बैकग्राउंड",
            "ar" to "قفل-خلفية",
            "pt" to "BLOQ-2ºP",
            "fr" to "VERR-ARP"
        ),
        "auto_lock_never" to mapOf(
            "en" to "Disabled (Never)",
            "ru" to "Отключено (Никогда)",
            "es" to "Desactivado (Nunca)",
            "zh" to "已禁用 (永不)",
            "hi" to "अक्षम (कभी नहीं)",
            "ar" to "معطل (أبدًا)",
            "pt" to "Desativado (Nunca)",
            "fr" to "Désactivé (Jamais)"
        ),
        "auto_lock_immediate" to mapOf(
            "en" to "Immediate on background",
            "ru" to "Сразу при сворачивании",
            "es" to "Inmediato en segundo plano",
            "zh" to "切入后台立即锁定",
            "hi" to "बैकग्राउंड पर तुरंत लॉक",
            "ar" to "فوري عند الخروج للخلفية",
            "pt" to "Imediato em segundo plano",
            "fr" to "Immédiat en arrière-plan"
        ),
        "auto_lock_preset_15s_label" to mapOf("en" to "15 SEC", "ru" to "15 СЕК", "es" to "15 SEG", "zh" to "15 秒", "hi" to "15 सेकंड", "ar" to "15 ثانية", "pt" to "15 SEG", "fr" to "15 SEC"),
        "auto_lock_preset_15s_desc" to mapOf("en" to "High Security - Instant lock", "ru" to "Макс. защита — быстрый блок", "es" to "Alta seguridad - Bloqueo rápido", "zh" to "高安全性 - 快速锁定", "hi" to "उच्च सुरक्षा", "ar" to "أمان عالي - قفل سريع", "pt" to "Alta segurança", "fr" to "Haute sécurité"),
        "auto_lock_preset_30s_label" to mapOf("en" to "30 SEC", "ru" to "30 СЕК", "es" to "30 SEG", "zh" to "30 秒", "hi" to "30 सेकंड", "ar" to "30 ثانية", "pt" to "30 SEG", "fr" to "30 SEC"),
        "auto_lock_preset_30s_desc" to mapOf("en" to "Paranoid Mode", "ru" to "Параноидальный режим", "es" to "Modo paranoico", "zh" to "高度戒备模式", "hi" to "सख्त सुरक्षा मोड", "ar" to "وضع الحماية القصوى", "pt" to "Modo paranoico", "fr" to "Mode paranoïaque"),
        "auto_lock_preset_60s_label" to mapOf("en" to "1 MIN", "ru" to "1 МИН", "es" to "1 MIN", "zh" to "1 分钟", "hi" to "1 मिनट", "ar" to "1 دقيقة", "pt" to "1 MIN", "fr" to "1 MIN"),
        "auto_lock_preset_60s_desc" to mapOf("en" to "Recommended Default", "ru" to "Рекомендуемый баланс", "es" to "Recomendado por defecto", "zh" to "推荐默认设置", "hi" to "अनुशंसित डिफ़ॉल्ट", "ar" to "الافتراضي الموصى به", "pt" to "Padrão recomendado", "fr" to "Défaut recommandé"),
        "auto_lock_preset_120s_label" to mapOf("en" to "2 MIN", "ru" to "2 МИН", "es" to "2 MIN", "zh" to "2 分钟", "hi" to "2 मिनट", "ar" to "2 دقيقة", "pt" to "2 MIN", "fr" to "2 MIN"),
        "auto_lock_preset_120s_desc" to mapOf("en" to "Standard Session", "ru" to "Стандартная сессия", "es" to "Sesión estándar", "zh" to "标准会话时长", "hi" to "मानक सत्र", "ar" to "جلسة قياسية", "pt" to "Sessão padrão", "fr" to "Session standard"),
        "auto_lock_preset_300s_label" to mapOf("en" to "5 MIN", "ru" to "5 МИН", "es" to "5 MIN", "zh" to "5 分钟", "hi" to "5 मिनट", "ar" to "5 دقائق", "pt" to "5 MIN", "fr" to "5 MIN"),
        "auto_lock_preset_300s_desc" to mapOf("en" to "Extended Session", "ru" to "Расширенная сессия", "es" to "Sesión extendida", "zh" to "延长会话时长", "hi" to "विस्तारित सत्र", "ar" to "جلسة ممتدة", "pt" to "Sessão estendida", "fr" to "Session prolongée"),
        "auto_lock_preset_600s_label" to mapOf("en" to "10 MIN", "ru" to "10 МИН", "es" to "10 MIN", "zh" to "10 分钟", "hi" to "10 मिनट", "ar" to "10 دقائق", "pt" to "10 MIN", "fr" to "10 MIN"),
        "auto_lock_preset_600s_desc" to mapOf("en" to "Maximum Inactivity", "ru" to "Максимум ожидания", "es" to "Inactividad máxima", "zh" to "最长无操作时间", "hi" to "अधिकतम निष्क्रियता", "ar" to "الحد الأقصى للخمول", "pt" to "Inatividade máxima", "fr" to "Inactivité maximale"),
        "auto_lock_preset_immediate_label" to mapOf("en" to "IMMEDIATE", "ru" to "СРАЗУ", "es" to "INMEDIATO", "zh" to "立即锁定", "hi" to "तुरंत", "ar" to "فوري", "pt" to "IMEDIATO", "fr" to "IMMÉDIAT"),
        "auto_lock_preset_immediate_desc" to mapOf("en" to "Lock on app background", "ru" to "Блок при сворачивании", "es" to "Al pasar a segundo plano", "zh" to "切入后台即锁定", "hi" to "बैकग्राउंड पर लॉक", "ar" to "قفل عند الخروج للخلفية", "pt" to "Ao ir para 2º plano", "fr" to "En arrière-plan"),
        "auto_lock_preset_never_label" to mapOf("en" to "NEVER", "ru" to "НИКОГДА", "es" to "NUNCA", "zh" to "永不", "hi" to "कभी नहीं", "ar" to "أبدًا", "pt" to "NUNCA", "fr" to "JAMAIS"),
        "auto_lock_preset_never_desc" to mapOf("en" to "Manual lock only", "ru" to "Только ручной блок", "es" to "Solo bloqueo manual", "zh" to "仅手动锁定", "hi" to "केवल मैनुअल लॉक", "ar" to "قفل يدوي فقط", "pt" to "Apenas bloqueio manual", "fr" to "Verrouillage manuel uniquement"),
        "kbd_done" to mapOf(
            "en" to "DONE",
            "ru" to "ГОТОВО",
            "es" to "LISTO",
            "zh" to "完成",
            "hi" to "पूर्ण",
            "ar" to "تم",
            "pt" to "OK",
            "fr" to "OK"
        ),
        "kbd_scramble" to mapOf(
            "en" to "SCRAMBLE",
            "ru" to "ПЕРЕМЕШАТЬ",
            "es" to "MEZCLAR",
            "zh" to "乱序",
            "hi" to "शफ़ल",
            "ar" to "خلط",
            "pt" to "EMBARALHAR",
            "fr" to "MÉLANGER"
        )
    )

    fun get(key: String): String {
        val lang = _currentLanguage.value.code
        return translations[key]?.get(lang) ?: translations[key]?.get("en") ?: key
    }
}
