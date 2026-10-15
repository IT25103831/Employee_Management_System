<aside class="sidebar" id="sidebar">
 <a class="brand" href="${pageContext.request.contextPath}/app/dashboard"><span class="brand-mark">PS</span><span><b>PeopleSphere</b><small>Employee Management</small></span></a>
 <nav>
  <a class="${module=='dashboard'?'active':''}" href="${pageContext.request.contextPath}/app/dashboard"><i>01</i>Dashboard</a>
  <c:if test="${currentUser.role=='HR_MANAGER'||currentUser.role=='IT_SYSTEM_ADMINISTRATOR'||currentUser.role=='OPERATIONS_MANAGER'}"><a class="${module=='employees'?'active':''}" href="${pageContext.request.contextPath}/app/employees"><i>02</i>Employees</a></c:if>
  <c:if test="${currentUser.role=='EMPLOYEE'||currentUser.role=='ATTENDANCE_COORDINATOR'||currentUser.role=='DEPARTMENT_SUPERVISOR'||currentUser.role=='PAYROLL_SPECIALIST'}"><a class="${module=='attendance'?'active':''}" href="${pageContext.request.contextPath}/app/attendance"><i>03</i>Attendance</a></c:if>
  <c:if test="${currentUser.role=='EMPLOYEE'||currentUser.role=='DEPARTMENT_SUPERVISOR'||currentUser.role=='HR_MANAGER'}"><a class="${module=='leave'?'active':''}" href="${pageContext.request.contextPath}/app/leave"><i>04</i>Leave</a></c:if>
  <c:if test="${currentUser.role=='EMPLOYEE'||currentUser.role=='DEPARTMENT_SUPERVISOR'||currentUser.role=='HR_MANAGER'||currentUser.role=='OPERATIONS_MANAGER'}"><a class="${module=='performance'?'active':''}" href="${pageContext.request.contextPath}/app/performance"><i>05</i>Performance</a></c:if>
  <c:if test="${currentUser.role=='EMPLOYEE'||currentUser.role=='PAYROLL_SPECIALIST'||currentUser.role=='OPERATIONS_MANAGER'}"><a class="${module=='payroll'?'active':''}" href="${pageContext.request.contextPath}/app/payroll"><i>06</i>Payroll</a></c:if>
  <c:if test="${currentUser.role=='IT_SYSTEM_ADMINISTRATOR'}"><a class="${module=='access'?'active':''}" href="${pageContext.request.contextPath}/app/access"><i>07</i>User Access</a></c:if>
  <c:if test="${currentUser.role!='EMPLOYEE'}"><a class="${module=='reports'?'active':''}" href="${pageContext.request.contextPath}/app/reports"><i>08</i>Reports</a></c:if>
  <a class="${module=='account'?'active':''}" href="${pageContext.request.contextPath}/app/account"><i>09</i>My Account</a>
 </nav>
 <div class="sidebar-foot"><span class="avatar"><c:out value="${currentUser.fullName.substring(0,1)}"/></span><div><b><c:out value="${currentUser.fullName}"/></b><small><c:out value="${currentUser.role.replace('_',' ')}"/></small></div></div>
</aside>