package com.classcue.kexing.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Built-in expressions commonly used by teachers in Chinese classrooms.
 *
 * <p>The phrases are intentionally split from user-defined "strong" keywords:
 * built-in phrases provide question/call context, while a custom strong keyword
 * can trigger an alert on its own. This keeps a large vocabulary practical
 * without turning every lecture sentence into a high-confidence alert.</p>
 */
final class ClassroomCueLexicon {
    static final List<String> CALL_CUES = terms(
            // Attendance and roll call.
            "点名", "开始点名", "现在点名", "下面点名", "继续点名", "随机点名",
            "抽查点名", "课堂点名", "签到", "开始签到", "现在签到", "扫码签到",
            "考勤", "查考勤", "清点人数", "看一下谁没来", "谁没来", "有谁没来",
            "到齐了吗", "都到了吗", "没到的", "没来的", "缺席的", "请假的",
            "旷课的", "应到", "实到", "答到", "喊到答到", "叫到答到",
            "念到答到", "听到名字答到", "报到", "报学号", "说一下学号",
            "喊到名字", "念到名字", "叫到名字", "在不在", "在吗", "来了没有",
            "到没到",

            // Selecting or describing a student.
            "哪位同学", "这位同学", "那位同学", "前排同学", "后排同学",
            "最后一排", "靠窗的同学", "靠门的同学", "戴眼镜的同学",
            "穿黑衣服的同学", "穿白衣服的同学", "举手的同学", "没举手的同学",
            "请一位同学", "找一位同学", "点一位同学", "抽一位同学",
            "随机抽一个", "随机点一个", "我点一个", "我找一个", "我叫一个",
            "下一位", "下一个同学", "轮到你", "请起立", "站起来", "你来一下",

            // Direct answer requests.
            "你来回答", "你来说", "你来说说", "你谈谈", "请你回答", "请你说",
            "请你说说", "请你来", "请回答", "起来回答", "上来回答", "到前面来",
            "上黑板", "到黑板上", "写到黑板上", "举手回答", "谁举手",
            "有没有同学", "谁愿意", "谁来试试", "谁来回答", "谁能回答",
            "谁知道", "有人回答吗", "没有人回答吗"
    );

    static final List<String> QUESTION_CUES = terms(
            // Interrogatives and answer-oriented sentence patterns.
            "为什么", "为什么呢", "为什么说", "怎么", "怎么做", "怎么理解",
            "怎么解释", "怎么判断", "如何", "如何理解", "如何证明", "如何计算",
            "如何分析", "什么", "是什么", "指什么", "什么意思", "多少", "是多少",
            "几个", "哪一个", "哪个", "哪种", "哪些", "哪一项", "谁", "何时",
            "什么时候", "哪里", "哪儿", "何处", "是否", "是不是", "能不能",
            "可不可以", "对不对", "有没有", "有没有可能", "有何", "有何不同",
            "有何联系", "有何意义", "有何作用", "怎么办", "原因是什么",
            "结果是什么", "条件是什么", "结论是什么", "答案是什么", "选什么",
            "应该选什么", "这题选什么", "这道题怎么做", "这一题怎么做",
            "这个怎么算", "如何作答",

            // Classroom task verbs.
            "请回答", "回答一下", "回答这个问题", "作答", "请作答", "说一下",
            "说一说", "说说", "谈一谈", "谈谈", "讲一下", "讲一讲",
            "解释一下", "解释解释", "分析一下", "分析分析", "说明一下",
            "说明原因", "阐述一下", "论述一下", "讨论一下", "大家讨论",
            "想一想", "思考一下", "考虑一下", "判断一下", "判断对错",
            "选择一下", "选一选", "填一下", "填空", "计算一下", "算一下",
            "算一算", "推导一下", "推一推", "证明一下", "验证一下", "检验一下",
            "画一下", "画图", "作图", "写一下", "写出", "列出", "列举",
            "举例", "举个例子", "举一个例子", "给个例子", "概括一下",
            "归纳一下", "总结一下", "小结一下", "复述一下", "翻译一下",
            "朗读一下", "读一下", "读一遍", "念一下", "背一下", "比较一下",
            "对比一下", "评价一下", "评论一下", "预测一下",

            // Common question targets in university classes.
            "区别", "不同点", "相同点", "共同点", "联系", "关系", "作用", "意义",
            "原因", "影响", "特点", "特征", "性质", "优点", "缺点", "优缺点",
            "依据", "条件", "前提", "结论", "定义", "概念", "公式", "原理",
            "过程", "步骤", "方法", "思路", "答案", "观点", "看法", "启示",
            "判断依据", "理论依据", "现实意义", "实际意义", "主要内容",
            "中心思想", "核心观点", "关键词", "主题是什么", "作者为什么",
            "说明了什么", "体现了什么", "反映了什么", "告诉我们什么",
            "能得出什么", "可以得出什么", "由此可见什么",

            // Requests that often precede selecting a student.
            "谁来回答", "谁能回答", "谁知道", "谁来说说", "谁来讲讲",
            "谁来分析", "谁来解释", "谁来总结", "谁来概括", "谁来证明",
            "谁来计算", "谁来推导", "谁来翻译", "谁来朗读", "哪位同学回答",
            "哪位同学来说", "哪位同学分析", "哪位同学解释", "请一位同学回答",
            "请同学回答", "请大家回答", "大家怎么看", "大家想一想",
            "同学们想一想", "有没有人知道", "有人知道吗", "有没有不同意见",
            "还有补充吗", "谁来补充", "谁有补充", "谁有不同意见",
            "还有别的答案吗", "还有其他答案吗", "赞成还是反对",
            "正确还是错误", "对还是错"
    );

    /**
     * Phrases that show the teacher is addressing students and expects someone to answer.
     * A generic question only becomes a "whole-class question" alert when it comes with one of
     * these or with a call/roll-call expression; rhetorical lecture questions such as
     * "这个公式是怎么推导出来的" no longer alert on their own.
     */
    static final List<String> AUDIENCE_CUES = terms(
            "谁来", "谁能", "谁会", "谁想", "谁有", "谁愿意", "谁知道", "谁举手",
            "哪位同学", "哪个同学", "哪位来", "有没有同学", "有没有人", "有人知道", "有人能",
            "请一位同学", "请个同学", "请同学", "请大家", "请你", "你来", "你说说", "你觉得",
            "你认为", "你们觉得", "你们认为", "你们说说", "你们怎么看", "你们想一想",
            "大家想一想", "大家想想", "大家思考", "大家说说", "大家说一下", "大家怎么看",
            "大家觉得", "大家讨论", "大家回答", "大家一起回答", "同学们想一想", "同学们说说",
            "同学们觉得", "同学们思考", "举手回答", "来回答", "起来回答", "上来", "到黑板"
    );

    private ClassroomCueLexicon() {}

    static int size() {
        LinkedHashSet<String> unique = new LinkedHashSet<>(CALL_CUES);
        unique.addAll(QUESTION_CUES);
        unique.addAll(AUDIENCE_CUES);
        return unique.size();
    }

    private static List<String> terms(String... values) {
        LinkedHashSet<String> unique = new LinkedHashSet<>(Arrays.asList(values));
        unique.remove("");
        return Collections.unmodifiableList(new ArrayList<>(unique));
    }
}
