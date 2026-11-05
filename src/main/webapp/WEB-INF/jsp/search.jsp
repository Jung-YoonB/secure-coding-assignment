<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!DOCTYPE html>
<html lang="ko">
<head><meta charset="UTF-8"><title>대출 상품 검색</title></head>
<body>
<h1>대출 상품 검색</h1>
<!-- 안전: 서버 출력은 JSTL <c:out> 또는 fn:escapeXml 은 HTML 이스케이프 처리 -->
<p>검색어: <span><c:out value="${q}" /></span></p>
<p>검색어(재표시): <span>${fn:escapeXml(param.q)}</span></p>

<!-- 안전: JavaScript DOM 조작 시 textContent 사용 -->
<p>검색 결과 요약: <span id="summary"></span></p>
<script>
	// 브라우저 출력은 textContent 사용 (문자열을 HTML 로 해석하지 않음)
    const q = new URLSearchParams(location.search).get("q") || "";
    document.getElementById("summary").textContent = q + " 에 대한 검색 결과가 없습니다.";
</script>
</body>
</html>
