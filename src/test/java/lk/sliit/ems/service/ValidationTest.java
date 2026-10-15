package lk.sliit.ems.service;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.*;import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class ValidationTest {
 private final LocalDate today=LocalDate.of(2026,9,30);
 @ParameterizedTest @ValueSource(strings={"","a","a@","a b@x.lk","a@b","a@@b.lk"}) void badEmail(String s){assertThrows(IllegalArgumentException.class,()->Validation.email(s));}
 @Test void email(){assertDoesNotThrow(()->Validation.email("user+work@example.lk"));}
 @ParameterizedTest @ValueSource(strings={"","1234","phone","abc0771234567"}) void badPhone(String s){assertThrows(IllegalArgumentException.class,()->Validation.phone(s));}
 @Test void phone(){assertDoesNotThrow(()->Validation.phone("+94 77 123 4567"));}
 @ParameterizedTest @ValueSource(strings={"","shortA1","alllower123","NO_DIGITS"}) void badPassword(String s){assertThrows(IllegalArgumentException.class,()->Validation.password(s));}
 @Test void employment(){assertDoesNotThrow(()->Validation.employment(today.minusYears(20),today,today));assertThrows(IllegalArgumentException.class,()->Validation.employment(today.minusYears(15),today,today));assertThrows(IllegalArgumentException.class,()->Validation.employment(today.minusYears(30),today.plusDays(1),today));}
 @Test void pastLeave(){assertThrows(IllegalArgumentException.class,()->Validation.leaveDays(today.minusDays(1),today,today));}
 @Test void reversedLeave(){assertThrows(IllegalArgumentException.class,()->Validation.leaveDays(today,today.minusDays(1),today));}
 @Test void crossYearLeave(){assertThrows(IllegalArgumentException.class,()->Validation.leaveDays(LocalDate.of(2026,12,31),LocalDate.of(2027,1,1),today));}
 @Test void inclusiveLeave(){assertEquals(1,Validation.leaveDays(today,today,today));assertEquals(3,Validation.leaveDays(today,today.plusDays(2),today));}
 @ParameterizedTest @ValueSource(strings={"0","5.01","-1","3.123"}) void badRating(String s){assertThrows(IllegalArgumentException.class,()->Validation.rating(new BigDecimal(s)));}
 @Test void ratingEdges(){Validation.rating(BigDecimal.ONE);Validation.rating(new BigDecimal("5.00"));}
 @Test void attendanceBoundaries(){LocalDateTime in=today.atTime(8,30),now=today.atTime(18,0);Validation.attendance(today,in,today.atTime(17,0),now);assertThrows(IllegalArgumentException.class,()->Validation.attendance(today,in,in.minusMinutes(1),now));assertThrows(IllegalArgumentException.class,()->Validation.attendance(today,in,now.plusMinutes(1),now));assertThrows(IllegalArgumentException.class,()->Validation.attendance(today,in.minusDays(1),null,now));}
 @Test void overnight(){LocalDateTime in=today.atTime(22,0),out=today.plusDays(1).atTime(6,0);Validation.attendance(today,in,out,out);}
 @Test void range(){assertThrows(IllegalArgumentException.class,()->Validation.range(today,today.minusDays(1)));Validation.range(today,today.plusDays(6));}
}
