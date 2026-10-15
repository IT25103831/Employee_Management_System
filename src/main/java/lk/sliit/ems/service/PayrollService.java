package lk.sliit.ems.service;
import java.math.BigDecimal;import java.math.RoundingMode;
public final class PayrollService{
    private PayrollService(){}
    public static BigDecimal percent(BigDecimal amount,BigDecimal rate){return amount.multiply(rate).divide(BigDecimal.valueOf(100),2,RoundingMode.HALF_UP);}
    public static BigDecimal net(BigDecimal gross,BigDecimal employeeEpf,BigDecimal deductions){return gross.subtract(employeeEpf).subtract(deductions).setScale(2,RoundingMode.HALF_UP);}
}
