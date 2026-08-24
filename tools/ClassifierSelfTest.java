import com.classcue.kexing.engine.*;
import java.util.Arrays;
import java.util.Collections;

public final class ClassifierSelfTest {
    private static int checks = 0;

    public static void main(String[] args) {
        ClassifierConfig config = new ClassifierConfig(
                "李明", "TEST0001", Arrays.asList("小李", "李同学"),
                Arrays.asList("后排", "第三组"), 3, true, true, false
        );
        expect("李明到了吗", config, CueType.ROLL_CALL);
        expect("TEST0001同学在不在", config, CueType.ROLL_CALL);
        expect("小李，你解释一下线性代数的特征值是什么", config, CueType.DIRECT_QUESTION);
        expect("李同学怎么看这个问题", config, CueType.DIRECT_QUESTION);
        expect("大家翻到43页", config, CueType.NONE);
        expect("特征值是什么", config, CueType.NONE);
        expect("今天我们学习线性代数", config, CueType.NONE);

        ClassifierConfig broad = new ClassifierConfig(
                "李明", "", Collections.emptyList(), Collections.emptyList(),
                5, true, true, true
        );
        expect("谁来回答一下什么是机会成本", broad, CueType.GENERAL_QUESTION);
        expect("李明", broad, CueType.ROLL_CALL);
        expect("", broad, CueType.NONE);

        ClassifierConfig strictStrongKeyword = new ClassifierConfig(
                "李明", "", Collections.emptyList(), Arrays.asList("特殊口令"),
                1, true, true, true
        );
        expect("老师说现在开始点名，听到特殊口令的同学请准备",
                strictStrongKeyword, CueType.ROLL_CALL);
        checks++;
        if (!CueClassifier.containsCustomKeyword(
                "这是还没有结束的一整句话，其中已经出现特殊口令但仍在继续识别",
                strictStrongKeyword.customKeywords
        )) {
            throw new AssertionError("Strong keyword in a partial longer sentence was not found");
        }
        System.out.println("Classifier self-test passed: " + checks + " checks");
    }

    private static void expect(String text, ClassifierConfig config, CueType expected) {
        checks++;
        CueResult actual = CueClassifier.classify(text, config);
        if (actual.type != expected) {
            throw new AssertionError("For [" + text + "] expected " + expected
                    + " but got " + actual.type + " score=" + actual.confidence);
        }
    }
}
