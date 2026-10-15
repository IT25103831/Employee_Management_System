<%@ include file="includes/head.jsp" %><div class="app-shell"><%@ include file="includes/sidebar.jsp" %><main class="workspace"><%@ include file="includes/topbar.jsp" %><div class="content">
<dialog id="salaryConfirmation" open class="panel salary-confirm"><h2>Confirm salary change</h2>
<p><b>Employee:</b> <c:out value="${change.employee_code}"/> — <c:out value="${change.full_name}"/></p>
<p><b>Old salary:</b> LKR <c:out value="${change.old_salary}"/></p>
<p><b>New salary:</b> LKR <c:out value="${newSalary}"/></p>
<p><b>Change:</b> ${difference>0?'+':''}LKR <c:out value="${difference}"/></p>
<p><b>Type:</b> ${difference>0?'Increase':difference<0?'Decrease':'No base salary change'}</p>
<c:if test="${param.action=='update'}"><p>Allowances: LKR <c:out value="${param.allowances}"/>; other deductions: LKR <c:out value="${param.deductions}"/>.</p></c:if>
<c:if test="${param.action=='salary'}"><p>Effective from: <c:out value="${param.effectiveDate}"/>. Old salary is the latest recorded salary structure.</p></c:if>
<form method="post"><input type="hidden" name="csrf" value="${sessionScope.csrf}"><input type="hidden" name="confirmed" value="yes"><input type="hidden" name="expectedBase" value="${change.old_salary}">
<c:forEach items="${['action','id','employeeId','base','allowances','deductions','effectiveDate']}" var="field"><input type="hidden" name="${field}" value="${fn:escapeXml(param[field])}"></c:forEach>
<button class="button primary">Confirm</button> <a class="button" href="${pageContext.request.contextPath}/app/payroll">Cancel</a></form></dialog>
<script>const popup=document.getElementById('salaryConfirmation');if(popup.showModal){popup.close();popup.showModal();popup.addEventListener('cancel',()=>{window.location.href='${pageContext.request.contextPath}/app/payroll';});}</script>
</div><%@ include file="includes/foot.jsp" %>
