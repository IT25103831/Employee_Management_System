<c:if test="${not empty profile}"><section class="panel"><h2>My Profile</h2>
<c:if test="${profile.has_photo==1}"><img class="profile-photo" src="${pageContext.request.contextPath}/profile-photo" alt="Your profile picture"></c:if>
<dl class="profile-grid">
<dt>Full name</dt><dd><c:out value="${profile.full_name}"/></dd>
<dt>Employee code</dt><dd><c:out value="${profile.employee_code}"/></dd>
<dt>NIC</dt><dd><c:out value="${profile.nic}"/></dd>
<dt>Date of birth</dt><dd><c:out value="${profile.date_of_birth}"/></dd>
<dt>Email</dt><dd><c:out value="${profile.email}"/></dd>
<dt>Phone</dt><dd><c:out value="${profile.phone}"/></dd>
<dt>Address</dt><dd><c:out value="${profile.address}"/></dd>
<dt>Department</dt><dd><c:out value="${profile.department_name}"/></dd>
<dt>Designation</dt><dd><c:out value="${profile.designation_name}"/></dd>
<dt>Employment date</dt><dd><c:out value="${profile.employment_date}"/></dd>
<dt>Employment status</dt><dd><c:out value="${profile.status}"/></dd>
</dl>
<form method="post" action="${pageContext.request.contextPath}/profile-photo" enctype="multipart/form-data" class="form-grid"><input type="hidden" name="csrf" value="${sessionScope.csrf}"><label>Profile picture (PNG/JPEG, up to 2 MB)<input type="file" name="photo" accept="image/png,image/jpeg" required></label><button class="button primary">Update picture</button></form>
</section></c:if>
