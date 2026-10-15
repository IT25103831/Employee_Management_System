package lk.sliit.ems.service;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;

/** Business rules shared by HTTP-backed repository operations and regression tests. */
public final class Validation {
    private Validation() {}
    public static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
    public static void text(String value, String field, int max) {
        require(value != null && !value.isBlank() && value.length() <= max, field + " is required (maximum " + max + " characters).");
    }
    public static void email(String value) {
        text(value,"Email",150);
        require(value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"),"Enter a valid email address.");
    }
    public static void phone(String value) {
        text(value,"Phone",20);
        require(value.matches("\\+?[0-9 ()-]{9,20}") && value.replaceAll("\\D", "").length() >= 9,"Enter a valid phone number.");
    }
    public static void password(String value) {
        require(value != null && value.length() >= 8 && value.length() <= 128 && value.matches(".*[A-Z].*") && value.matches(".*[0-9].*"),"Password needs 8–128 characters, an uppercase letter and a number.");
    }
    public static void employment(LocalDate birth, LocalDate joined, LocalDate today) {
        require(birth.isBefore(today) && !joined.isBefore(birth.plusYears(16)) && !joined.isAfter(today),"Employment date must be today or earlier and employee must be at least 16 when joining.");
    }
    public static int leaveDays(LocalDate start, LocalDate end, LocalDate today) {
        require(!start.isBefore(today),"Leave cannot start in the past.");
        require(!end.isBefore(start),"End date must not precede start date.");
        require(start.getYear()==end.getYear(),"Split leave across calendar years into separate requests.");
        return Math.toIntExact(ChronoUnit.DAYS.between(start,end)+1);
    }
    public static void range(LocalDate start, LocalDate end) {
        require(start != null && end != null && !end.isBefore(start),"Select a valid date range.");
        require(ChronoUnit.DAYS.between(start,end)<=3660,"Date range cannot exceed ten years.");
    }
    public static void rating(BigDecimal rating) { decimal(rating,"Rating",BigDecimal.ONE,new BigDecimal("5")); }
    public static void decimal(BigDecimal value,String field,BigDecimal min,BigDecimal max) {
        require(value!=null && value.compareTo(min)>=0 && value.compareTo(max)<=0 && value.scale()<=2,field+" is outside the valid range or has more than two decimal places.");
    }
    public static void attendance(LocalDate date, LocalDateTime in, LocalDateTime out, LocalDateTime now) {
        require(in!=null && !in.toLocalDate().isBefore(date) && !in.toLocalDate().isAfter(date.plusDays(1)) && !in.isAfter(now),"Clock-in must belong to the working date or its overnight continuation, and cannot be in the future.");
        require(out==null || (!out.isBefore(in) && !out.isAfter(now) && !out.isAfter(in.plusHours(24))),"Clock-out must follow clock-in, be within 24 hours, and not be in the future.");
    }
}
