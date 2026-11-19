// 카카오 우편번호 검색 결과(data)를 화면에 넣을 기본주소 문자열로 만든다.
// 내 정보 수정(user/update-form)과 구매·낙찰 잔금 결제 배송지(fragments/delivery-address)가 같이 쓴다.
// - 도로명 주소를 고르면 참고항목(법정동, 공동주택 이름)을 괄호로 붙인다. 예: "서울 용산구 유엔빌리지길 1 (한남동)"
// - 지번 주소를 고르면 지번 주소 그대로
window.formatPostcodeAddress = function (data) {
    if (data.userSelectedType !== 'R') {
        return data.jibunAddress;
    }
    let extra = '';
    // 법정동명이 있으면 붙인다 (법정리는 제외: 법정동은 "동/로/가"로 끝난다)
    if (data.bname !== '' && /[동로가]$/.test(data.bname)) {
        extra += data.bname;
    }
    // 건물명이 있고 공동주택이면 붙인다
    if (data.buildingName !== '' && data.apartment === 'Y') {
        extra += (extra !== '' ? ', ' : '') + data.buildingName;
    }
    return data.roadAddress + (extra !== '' ? ' (' + extra + ')' : '');
};
