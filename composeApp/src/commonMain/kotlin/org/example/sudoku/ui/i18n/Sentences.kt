package org.example.sudoku.ui.i18n

/**
 * 首页随机句子的**内置语料**。
 *
 * **为什么内置而不是运行时拉网**：本作的设计红线是「单机 · 无需联网」（`docs/02` §1、README 首条卖点），
 * 运行时抓取要引入网络栈、错误处理与权限，还会让首页在离线时开天窗。
 * 因此采用「开发期收集、打包内置」：下面的句子都收集自公开网络资料
 * （古典诗文为公有领域，名人名言附出处，脑筋急转弯为流传较广的民间段子），
 * "获取"= 启动时装入内存，"缓存"= 会话内记住最近出过的句子避免连续重复，"轮播"= 定时 + 手动。
 *
 * 以后若要做在线语料，只需把这里的来源换成"内置 + 拉取合并"，轮播与缓存逻辑不变。
 */
enum class SentenceCategory {
    /** 名人名言。 */
    Quote,

    /** 诗词歌赋。 */
    Verse,

    /** 脑筋急转弯（[Sentence.answer] 是答案，翻面才显示）。 */
    Riddle,
}

data class Sentence(
    val category: SentenceCategory,
    /** 正文。 */
    val text: String,
    /** 出处 / 作者 / 答案（脑筋急转弯 = 答案）。 */
    val source: String,
)

/** 语料库：轮播时在**全库**里随机（避免只在一个类别里打转）。 */
object SentenceBook {
    val all: List<Sentence> = listOf(
        // —— 名人名言 ——
        Sentence(SentenceCategory.Quote, "学而不思则罔，思而不学则殆。", "孔子 ·《论语》"),
        Sentence(SentenceCategory.Quote, "千里之行，始于足下。", "老子 ·《道德经》"),
        Sentence(SentenceCategory.Quote, "路漫漫其修远兮，吾将上下而求索。", "屈原 ·《离骚》"),
        Sentence(SentenceCategory.Quote, "不积跬步，无以至千里；不积小流，无以成江海。", "荀子 ·《劝学》"),
        Sentence(SentenceCategory.Quote, "业精于勤，荒于嬉；行成于思，毁于随。", "韩愈 ·《进学解》"),
        Sentence(SentenceCategory.Quote, "纸上得来终觉浅，绝知此事要躬行。", "陆游 ·《冬夜读书示子聿》"),
        Sentence(SentenceCategory.Quote, "问渠那得清如许？为有源头活水来。", "朱熹 ·《观书有感》"),
        Sentence(SentenceCategory.Quote, "Genius is one percent inspiration and ninety-nine percent perspiration.", "Thomas Edison"),
        Sentence(SentenceCategory.Quote, "It always seems impossible until it is done.", "Nelson Mandela"),
        Sentence(SentenceCategory.Quote, "Simplicity is the ultimate sophistication.", "Leonardo da Vinci"),
        Sentence(SentenceCategory.Quote, "We are what we repeatedly do. Excellence, then, is not an act but a habit.", "Aristotle"),
        // —— 诗词歌赋 ——
        Sentence(SentenceCategory.Verse, "会当凌绝顶，一览众山小。", "杜甫 ·《望岳》"),
        Sentence(SentenceCategory.Verse, "长风破浪会有时，直挂云帆济沧海。", "李白 ·《行路难》"),
        Sentence(SentenceCategory.Verse, "山重水复疑无路，柳暗花明又一村。", "陆游 ·《游山西村》"),
        Sentence(SentenceCategory.Verse, "沉舟侧畔千帆过，病树前头万木春。", "刘禹锡 ·《酬乐天扬州初逢席上见赠》"),
        Sentence(SentenceCategory.Verse, "落霞与孤鹜齐飞，秋水共长天一色。", "王勃 ·《滕王阁序》"),
        Sentence(SentenceCategory.Verse, "大漠孤烟直，长河落日圆。", "王维 ·《使至塞上》"),
        Sentence(SentenceCategory.Verse, "采菊东篱下，悠然见南山。", "陶渊明 ·《饮酒·其五》"),
        Sentence(SentenceCategory.Verse, "海内存知己，天涯若比邻。", "王勃 ·《送杜少府之任蜀州》"),
        Sentence(SentenceCategory.Verse, "欲穷千里目，更上一层楼。", "王之涣 ·《登鹳雀楼》"),
        Sentence(SentenceCategory.Verse, "不畏浮云遮望眼，自缘身在最高层。", "王安石 ·《登飞来峰》"),
        Sentence(SentenceCategory.Verse, "忽如一夜春风来，千树万树梨花开。", "岑参 ·《白雪歌送武判官归京》"),
        Sentence(SentenceCategory.Verse, "等闲识得东风面，万紫千红总是春。", "朱熹 ·《春日》"),
        Sentence(SentenceCategory.Verse, "小荷才露尖尖角，早有蜻蜓立上头。", "杨万里 ·《小池》"),
        // —— 脑筋急转弯（answer 是答案，点一下翻面） ——
        Sentence(SentenceCategory.Riddle, "什么东西越洗越脏？", "水"),
        Sentence(SentenceCategory.Riddle, "什么车寸步难行？", "风车"),
        Sentence(SentenceCategory.Riddle, "小明的妈妈有三个孩子，老大叫大毛，老二叫二毛，老三叫什么？", "小明"),
        Sentence(SentenceCategory.Riddle, "什么东西打破了大家都高兴？", "世界纪录"),
        Sentence(SentenceCategory.Riddle, "一年四季都盛开的花是什么花？", "塑料花"),
        Sentence(SentenceCategory.Riddle, "什么门永远关不上？", "球门"),
        Sentence(SentenceCategory.Riddle, "什么样的路不能走？", "电路"),
        Sentence(SentenceCategory.Riddle, "什么水不能喝？", "薪水"),
        Sentence(SentenceCategory.Riddle, "什么东西有头无脚？", "砖头"),
        Sentence(SentenceCategory.Riddle, "什么东西越用越多？", "知识"),
    )

    /** 类别的展示名（语料是中文内容，标签也用中文；属"内容"而非界面文字，不进多语言字典）。 */
    fun categoryLabel(category: SentenceCategory): String = when (category) {
        SentenceCategory.Quote -> "名人名言"
        SentenceCategory.Verse -> "诗词歌赋"
        SentenceCategory.Riddle -> "脑筋急转弯"
    }
}
