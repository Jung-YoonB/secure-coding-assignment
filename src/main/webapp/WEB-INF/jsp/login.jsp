<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="ko">
<head><meta charset="UTF-8"><title>로그인 (과제용)</title></head>
<body>
<h1>과제용 로그인</h1>
<p>고객 ID만으로 세션을 만든다 (C001 / C002 / C003).</p>
<form action="${pageContext.request.contextPath}/login" method="post">
    고객 ID <input name="customerId" value="C001">
    <button type="submit">로그인</button>
</form>
</body>
</html>
