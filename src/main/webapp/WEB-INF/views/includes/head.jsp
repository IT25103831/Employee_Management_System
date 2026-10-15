<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!doctype html><html lang="en"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title><c:out value="${empty title ? 'PeopleSphere EMS' : title.concat(' | PeopleSphere EMS')}"/></title>
<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/modules/employee-profile.css">
<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/modules/attendance-shift.css">
<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/modules/user-access.css">
<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/modules/leave-absence.css">
<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/modules/performance-evaluation.css">
<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/modules/payroll-salary.css">
</head><body>
