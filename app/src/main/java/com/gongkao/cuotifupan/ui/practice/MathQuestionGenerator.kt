package com.gongkao.cuotifupan.ui.practice

import kotlin.random.Random

/**
 * 数学题生成器
 */
object MathQuestionGenerator {
    
    /**
     * 练习类型枚举
     */
    enum class PracticeType(val displayName: String) {
        TWO_DIGIT_ADD_SUB("两位数加减"),
        THREE_DIGIT_ADD("三位数加法"),
        THREE_DIGIT_SUB("三位数减法"),
        THREE_DIGIT_ADD_SUB("三位数加减"),
        MIXED_ADD_SUB("混合加减"),
        TWO_DIGIT_MUL_ONE("两位数乘一位数"),
        TWO_DIGIT_MUL_11("两位数乘11"),
        TWO_DIGIT_MUL_15("两位数乘15"),
        TWO_DIGIT_MUL_TWO("两位数乘两位数"),
        THREE_DIGIT_MUL_ONE("三位数乘一位数"),
        THREE_DIGIT_DIV_ONE("三位数除以一位数"),
        THREE_DIGIT_DIV_TWO("三位数除以两位数"),
        FIVE_DIGIT_DIV_THREE("五位数除以三位数"),
        THREE_DIGIT_DIV_FOUR("三位数除以四位数"),
        MULTI_NUMBER_ADD("多数相加"),
        ROUND_TO_HUNDRED("凑整百练习"),
        COMMON_SQUARES("常见平方数"),
        MULTIPLICATION_ESTIMATE("乘法估算"),
        FRACTION_CALC_NUM_LESS("分数计算(分子<分母)"),
        FRACTION_CALC_NUM_MORE("分数计算(分子>分母)"),
        FRACTION_COMPARE("分数比大小"),
        BASE_PERIOD_PROPORTION("基期比重"),
        ANNUAL_AVERAGE("年平均量"),
        ESTIMATE_PREVIOUS("估算前期量"),
        ESTIMATE_GROWTH("估算增长量"),
        PERCENTAGE_CALC("百化分计算"),
        INCREMENT_COMPARE("增量比大小"),
        BASE_PERIOD_COMPARE("基期比大小"),
        ANNUAL_GROWTH_RATE("年均增长率"),
        ONE_TABLE_CALC("一表通算")
    }
    
    /**
     * 数学题数据类
     */
    data class MathQuestion(
        val question: String,  // 题目表达式
        val answer: Double,    // 正确答案
        val practiceType: PracticeType
    )
    
    /**
     * 生成指定类型和数量的题目
     */
    fun generateQuestions(type: PracticeType, count: Int): List<MathQuestion> {
        return (1..count).map { generateQuestion(type) }
    }
    
    /**
     * 生成单个题目
     */
    private fun generateQuestion(type: PracticeType): MathQuestion {
        android.util.Log.d("MathQuestionGenerator", "生成题目，类型: ${type.name} (${type.displayName})")
        val question = when (type) {
            PracticeType.TWO_DIGIT_ADD_SUB -> generateTwoDigitAddSub()
            PracticeType.THREE_DIGIT_ADD -> generateThreeDigitAdd()
            PracticeType.THREE_DIGIT_SUB -> generateThreeDigitSub()
            PracticeType.THREE_DIGIT_ADD_SUB -> generateThreeDigitAddSub()
            PracticeType.MIXED_ADD_SUB -> generateMixedAddSub()
            PracticeType.TWO_DIGIT_MUL_ONE -> generateTwoDigitMulOne()
            PracticeType.TWO_DIGIT_MUL_11 -> generateTwoDigitMul11()
            PracticeType.TWO_DIGIT_MUL_15 -> generateTwoDigitMul15()
            PracticeType.TWO_DIGIT_MUL_TWO -> generateTwoDigitMulTwo()
            PracticeType.THREE_DIGIT_MUL_ONE -> generateThreeDigitMulOne()
            PracticeType.THREE_DIGIT_DIV_ONE -> generateThreeDigitDivOne()
            PracticeType.THREE_DIGIT_DIV_TWO -> generateThreeDigitDivTwo()
            PracticeType.FIVE_DIGIT_DIV_THREE -> generateFiveDigitDivThree()
            PracticeType.THREE_DIGIT_DIV_FOUR -> generateThreeDigitDivFour()
            PracticeType.MULTI_NUMBER_ADD -> generateMultiNumberAdd()
            PracticeType.ROUND_TO_HUNDRED -> generateRoundToHundred()
            PracticeType.COMMON_SQUARES -> generateCommonSquares()
            PracticeType.MULTIPLICATION_ESTIMATE -> generateMultiplicationEstimate()
            PracticeType.FRACTION_CALC_NUM_LESS -> generateFractionCalcNumLess()
            PracticeType.FRACTION_CALC_NUM_MORE -> generateFractionCalcNumMore()
            PracticeType.FRACTION_COMPARE -> generateFractionCompare()
            PracticeType.BASE_PERIOD_PROPORTION -> generateBasePeriodProportion()
            PracticeType.ANNUAL_AVERAGE -> generateAnnualAverage()
            PracticeType.ESTIMATE_PREVIOUS -> generateEstimatePrevious()
            PracticeType.ESTIMATE_GROWTH -> generateEstimateGrowth()
            PracticeType.PERCENTAGE_CALC -> generatePercentageCalc()
            PracticeType.INCREMENT_COMPARE -> generateIncrementCompare()
            PracticeType.BASE_PERIOD_COMPARE -> generateBasePeriodCompare()
            PracticeType.ANNUAL_GROWTH_RATE -> generateAnnualGrowthRate()
            PracticeType.ONE_TABLE_CALC -> generateOneTableCalc()
        }
        android.util.Log.d("MathQuestionGenerator", "生成的题目: ${question.question}, 类型: ${question.practiceType.name} (${question.practiceType.displayName})")
        // 确保生成的题目类型与请求的类型一致
        if (question.practiceType != type) {
            android.util.Log.e("MathQuestionGenerator", "⚠️ 类型不匹配！请求: ${type.name}, 实际: ${question.practiceType.name}")
        }
        return question
    }
    
    // 两位数加减
    private fun generateTwoDigitAddSub(): MathQuestion {
        val a = Random.nextInt(10, 100)
        val b = Random.nextInt(10, 100)
        val isAdd = Random.nextBoolean()
        val question = if (isAdd) "$a+$b" else "$a-$b"
        val answer = if (isAdd) (a + b).toDouble() else (a - b).toDouble()
        return MathQuestion(question, answer, PracticeType.TWO_DIGIT_ADD_SUB)
    }
    
    // 三位数加法
    private fun generateThreeDigitAdd(): MathQuestion {
        val a = Random.nextInt(100, 1000)
        val b = Random.nextInt(100, 1000)
        return MathQuestion("$a+$b", (a + b).toDouble(), PracticeType.THREE_DIGIT_ADD)
    }
    
    // 三位数减法
    private fun generateThreeDigitSub(): MathQuestion {
        val a = Random.nextInt(100, 1000)
        val b = Random.nextInt(100, a)
        return MathQuestion("$a-$b", (a - b).toDouble(), PracticeType.THREE_DIGIT_SUB)
    }
    
    // 三位数加减
    private fun generateThreeDigitAddSub(): MathQuestion {
        val a = Random.nextInt(100, 1000)
        val b = Random.nextInt(100, 1000)
        val isAdd = Random.nextBoolean()
        val question = if (isAdd) "$a+$b" else {
            val min = minOf(a, b)
            val max = maxOf(a, b)
            "$max-$min"
        }
        val answer = if (isAdd) (a + b).toDouble() else {
            val min = minOf(a, b)
            val max = maxOf(a, b)
            (max - min).toDouble()
        }
        return MathQuestion(question, answer, PracticeType.THREE_DIGIT_ADD_SUB)
    }
    
    // 混合加减
    private fun generateMixedAddSub(): MathQuestion {
        val a = Random.nextInt(10, 1000)
        val b = Random.nextInt(10, 1000)
        val c = Random.nextInt(10, 1000)
        val ops = listOf("+", "-")
        val op1 = ops.random()
        val op2 = ops.random()
        val question = "$a$op1$b$op2$c"
        val answer = when {
            op1 == "+" && op2 == "+" -> (a + b + c).toDouble()
            op1 == "+" && op2 == "-" -> (a + b - c).toDouble()
            op1 == "-" && op2 == "+" -> (a - b + c).toDouble()
            else -> (a - b - c).toDouble()
        }
        return MathQuestion(question, answer, PracticeType.MIXED_ADD_SUB)
    }
    
    // 两位数乘一位数
    private fun generateTwoDigitMulOne(): MathQuestion {
        val a = Random.nextInt(10, 100)
        val b = Random.nextInt(1, 10)
        return MathQuestion("$a×$b", (a * b).toDouble(), PracticeType.TWO_DIGIT_MUL_ONE)
    }
    
    // 两位数乘11
    private fun generateTwoDigitMul11(): MathQuestion {
        val a = Random.nextInt(10, 100)
        return MathQuestion("$a×11", (a * 11).toDouble(), PracticeType.TWO_DIGIT_MUL_11)
    }
    
    // 两位数乘15
    private fun generateTwoDigitMul15(): MathQuestion {
        val a = Random.nextInt(10, 100)
        return MathQuestion("$a×15", (a * 15).toDouble(), PracticeType.TWO_DIGIT_MUL_15)
    }
    
    // 两位数乘两位数
    private fun generateTwoDigitMulTwo(): MathQuestion {
        val a = Random.nextInt(10, 100)
        val b = Random.nextInt(10, 100)
        return MathQuestion("$a×$b", (a * b).toDouble(), PracticeType.TWO_DIGIT_MUL_TWO)
    }
    
    // 三位数乘一位数
    private fun generateThreeDigitMulOne(): MathQuestion {
        val a = Random.nextInt(100, 1000)
        val b = Random.nextInt(1, 10)
        return MathQuestion("$a×$b", (a * b).toDouble(), PracticeType.THREE_DIGIT_MUL_ONE)
    }
    
    // 三位数除以一位数
    private fun generateThreeDigitDivOne(): MathQuestion {
        val divisor = Random.nextInt(2, 10)
        // 确保被除数是三位数（100-999）
        // 被除数 = 除数 * 商，所以商的范围需要保证被除数在100-999之间
        val maxQuotient = 999 / divisor
        val minQuotient = (100 + divisor - 1) / divisor  // 向上取整，确保被除数>=100
        val quotient = Random.nextInt(minQuotient, maxQuotient + 1)
        val dividend = divisor * quotient
        // 确保被除数确实是三位数
        if (dividend < 100 || dividend >= 1000) {
            // 如果不符合，重新生成
            return generateThreeDigitDivOne()
        }
        return MathQuestion("$dividend÷$divisor", quotient.toDouble(), PracticeType.THREE_DIGIT_DIV_ONE)
    }
    
    // 三位数除以两位数
    private fun generateThreeDigitDivTwo(): MathQuestion {
        val divisor = Random.nextInt(10, 100)
        val quotient = Random.nextInt(10, 100)
        val dividend = divisor * quotient
        if (dividend >= 1000) {
            val newQuotient = dividend / divisor
            return MathQuestion("$dividend÷$divisor", newQuotient.toDouble(), PracticeType.THREE_DIGIT_DIV_TWO)
        }
        return MathQuestion("$dividend÷$divisor", quotient.toDouble(), PracticeType.THREE_DIGIT_DIV_TWO)
    }
    
    // 五位数除以三位数
    private fun generateFiveDigitDivThree(): MathQuestion {
        val divisor = Random.nextInt(100, 1000)
        val quotient = Random.nextInt(10, 100)
        val dividend = divisor * quotient
        return MathQuestion("$dividend÷$divisor", quotient.toDouble(), PracticeType.FIVE_DIGIT_DIV_THREE)
    }
    
    // 三位数除以四位数
    private fun generateThreeDigitDivFour(): MathQuestion {
        // 确保被除数是三位数（100-999），除数是四位数（1000-9999）
        val dividend = Random.nextInt(100, 1000)
        val divisor = Random.nextInt(1000, 10000)
        val quotient = dividend.toDouble() / divisor
        // 确保商是合理的小数（0.01到0.99之间）
        if (quotient < 0.01 || quotient >= 1.0) {
            // 如果商不在合理范围，重新生成
            return generateThreeDigitDivFour()
        }
        return MathQuestion("$dividend÷$divisor", quotient, PracticeType.THREE_DIGIT_DIV_FOUR)
    }
    
    // 多数相加
    private fun generateMultiNumberAdd(): MathQuestion {
        val count = Random.nextInt(3, 6)
        val numbers = (1..count).map { Random.nextInt(10, 1000) }
        val question = numbers.joinToString("+")
        val answer = numbers.sum().toDouble()
        return MathQuestion(question, answer, PracticeType.MULTI_NUMBER_ADD)
    }
    
    // 凑整百练习：一个数加多少是整百数（如：57加多少是100，333加多少是600）
    private fun generateRoundToHundred(): MathQuestion {
        // 生成一个起始数（可以是两位数或三位数）
        val startNum = Random.nextInt(10, 1000)
        // 生成目标整百数（100, 200, 300, ..., 1000）
        val targetHundred = Random.nextInt(1, 11) * 100  // 100到1000之间的整百数
        // 计算需要加多少
        val addNum = targetHundred - startNum
        // 确保结果是正数（如果startNum >= targetHundred，重新生成）
        if (addNum <= 0) {
            return generateRoundToHundred()
        }
        // 题目格式：57+?=100 或 333+?=600
        return MathQuestion("$startNum+?=$targetHundred", addNum.toDouble(), PracticeType.ROUND_TO_HUNDRED)
    }
    
    // 常见平方数
    private fun generateCommonSquares(): MathQuestion {
        val num = Random.nextInt(11, 30)
        return MathQuestion("${num}²", (num * num).toDouble(), PracticeType.COMMON_SQUARES)
    }
    
    // 乘法估算
    private fun generateMultiplicationEstimate(): MathQuestion {
        val a = Random.nextInt(10, 100)
        val b = Random.nextInt(10, 100)
        val estimate = ((a / 10) * 10) * ((b / 10) * 10)
        return MathQuestion("$a×$b≈", estimate.toDouble(), PracticeType.MULTIPLICATION_ESTIMATE)
    }
    
    // 分数计算(分子<分母)
    private fun generateFractionCalcNumLess(): MathQuestion {
        val numerator = Random.nextInt(1, 10)
        val denominator = Random.nextInt(numerator + 1, 20)
        val answer = numerator.toDouble() / denominator
        return MathQuestion("$numerator/$denominator", answer, PracticeType.FRACTION_CALC_NUM_LESS)
    }
    
    // 分数计算(分子>分母)
    private fun generateFractionCalcNumMore(): MathQuestion {
        val denominator = Random.nextInt(2, 10)
        val numerator = Random.nextInt(denominator + 1, denominator * 3)
        val answer = numerator.toDouble() / denominator
        return MathQuestion("$numerator/$denominator", answer, PracticeType.FRACTION_CALC_NUM_MORE)
    }
    
    // 分数比大小
    private fun generateFractionCompare(): MathQuestion {
        var num1: Int
        var den1: Int
        var num2: Int
        var den2: Int
        var val1: Double
        var val2: Double
        
        // 确保两边的结果不相等
        do {
            num1 = Random.nextInt(1, 10)
            den1 = Random.nextInt(num1 + 1, 20)
            num2 = Random.nextInt(1, 10)
            den2 = Random.nextInt(num2 + 1, 20)
            val1 = num1.toDouble() / den1
            val2 = num2.toDouble() / den2
        } while (kotlin.math.abs(val1 - val2) < 0.0001)  // 如果相等，重新生成
        
        val answer = if (val1 > val2) 1.0 else -1.0
        return MathQuestion("$num1/$den1 ? $num2/$den2", answer, PracticeType.FRACTION_COMPARE)
    }
    
    // 基期比重
    private fun generateBasePeriodProportion(): MathQuestion {
        val current = Random.nextInt(100, 1000)
        val growth = Random.nextInt(1, 100)
        val base = current - growth
        val answer = (base.toDouble() / (base + current)) * 100
        return MathQuestion("基期: $base, 现期: $current", answer, PracticeType.BASE_PERIOD_PROPORTION)
    }
    
    // 年平均量
    private fun generateAnnualAverage(): MathQuestion {
        val total = Random.nextInt(1000, 10000)
        val years = Random.nextInt(3, 10)
        val answer = total.toDouble() / years
        return MathQuestion("总量: $total, 年数: $years", answer, PracticeType.ANNUAL_AVERAGE)
    }
    
    // 估算前期量
    private fun generateEstimatePrevious(): MathQuestion {
        val current = Random.nextInt(100, 1000)
        val growthRate = Random.nextDouble(0.01, 0.5)
        val previous = (current / (1 + growthRate)).toInt()
        return MathQuestion("现期: $current, 增长率: ${(growthRate * 100).toInt()}%", previous.toDouble(), PracticeType.ESTIMATE_PREVIOUS)
    }
    
    // 估算增长量
    private fun generateEstimateGrowth(): MathQuestion {
        val current = Random.nextInt(100, 1000)
        val growthRate = Random.nextDouble(0.01, 0.5)
        val growth = (current * growthRate / (1 + growthRate)).toInt()
        return MathQuestion("现期: $current, 增长率: ${(growthRate * 100).toInt()}%", growth.toDouble(), PracticeType.ESTIMATE_GROWTH)
    }
    
    // 百化分计算
    private fun generatePercentageCalc(): MathQuestion {
        val num = Random.nextInt(1, 100)
        val percent = Random.nextDouble(0.01, 0.5)
        val answer = num * percent
        return MathQuestion("$num × ${(percent * 100).toInt()}%", answer, PracticeType.PERCENTAGE_CALC)
    }
    
    // 增量比大小
    private fun generateIncrementCompare(): MathQuestion {
        var a1: Int
        var r1: Double
        var a2: Int
        var r2: Double
        var inc1: Double
        var inc2: Double
        
        // 确保两边的结果不相等
        do {
            a1 = Random.nextInt(100, 1000)
            r1 = Random.nextDouble(0.01, 0.3)
            a2 = Random.nextInt(100, 1000)
            r2 = Random.nextDouble(0.01, 0.3)
            inc1 = a1 * r1 / (1 + r1)
            inc2 = a2 * r2 / (1 + r2)
        } while (kotlin.math.abs(inc1 - inc2) < 0.01)  // 如果相等（误差小于0.01），重新生成
        
        val answer = if (inc1 > inc2) 1.0 else -1.0
        return MathQuestion("A: $a1(${(r1*100).toInt()}%) vs B: $a2(${(r2*100).toInt()}%)", answer, PracticeType.INCREMENT_COMPARE)
    }
    
    // 基期比大小
    private fun generateBasePeriodCompare(): MathQuestion {
        var a1: Int
        var r1: Double
        var a2: Int
        var r2: Double
        var base1: Double
        var base2: Double
        
        // 确保两边的结果不相等
        do {
            a1 = Random.nextInt(100, 1000)
            r1 = Random.nextDouble(0.01, 0.3)
            a2 = Random.nextInt(100, 1000)
            r2 = Random.nextDouble(0.01, 0.3)
            base1 = a1 / (1 + r1)
            base2 = a2 / (1 + r2)
        } while (kotlin.math.abs(base1 - base2) < 0.01)  // 如果相等（误差小于0.01），重新生成
        
        val answer = if (base1 > base2) 1.0 else -1.0
        return MathQuestion("A: $a1(${(r1*100).toInt()}%) vs B: $a2(${(r2*100).toInt()}%)", answer, PracticeType.BASE_PERIOD_COMPARE)
    }
    
    // 年均增长率
    private fun generateAnnualGrowthRate(): MathQuestion {
        val base = Random.nextInt(100, 1000)
        val current = Random.nextInt((base * 1.5).toInt(), base * 3)
        val years = Random.nextInt(3, 10)
        val rate = Math.pow(current.toDouble() / base, 1.0 / years) - 1
        return MathQuestion("基期: $base, 现期: $current, 年数: $years", rate * 100, PracticeType.ANNUAL_GROWTH_RATE)
    }
    
    // 一表通算
    private fun generateOneTableCalc(): MathQuestion {
        val a = Random.nextInt(10, 100)
        val b = Random.nextInt(10, 100)
        val c = Random.nextInt(10, 100)
        val question = "$a+$b+$c"
        val answer = (a + b + c).toDouble()
        return MathQuestion(question, answer, PracticeType.ONE_TABLE_CALC)
    }
}

