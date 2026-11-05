<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="ko">
<head><meta charset="UTF-8"><title>대출 상환</title></head>
<body>
<h1>대출 상환</h1>
<form action="${pageContext.request.contextPath}/repay-form" method="post">
    <%-- 서버가 발급한 CSRF 토큰을 함께 전송 --%>
    <input type="hidden" name="_csrf" value="<c:out value='${csrf}' />">
    대출번호 <input name="loanId" value="1001">
    금액 <input name="amount" value="1000">
    <button type="submit">상환</button>
</form>
</body>
</html>