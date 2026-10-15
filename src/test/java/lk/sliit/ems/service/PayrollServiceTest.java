package lk.sliit.ems.service;
import org.junit.jupiter.api.Test;import java.math.BigDecimal;import static org.junit.jupiter.api.Assertions.assertEquals;
class PayrollServiceTest{
 @Test void calculatesPercentage(){assertEquals(new BigDecimal("8000.00"),PayrollService.percent(new BigDecimal("100000"),new BigDecimal("8")));}
 @Test void calculatesNet(){assertEquals(new BigDecimal("87000.00"),PayrollService.net(new BigDecimal("100000"),new BigDecimal("8000"),new BigDecimal("5000")));}
}
