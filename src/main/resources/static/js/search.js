// 선택 상자를 바꾸면 바로 적용하고, "내 주변" 버튼으로 현재 위치를 조건에 붙인다.
(function () {
    'use strict';

    document.querySelectorAll('select[data-autosubmit]').forEach(function (select) {
        select.addEventListener('change', function () {
            select.form.submit();
        });
    });

    var nearMe = document.getElementById('near-me');
    if (!nearMe) return;

    nearMe.addEventListener('click', function () {
        var params = new URLSearchParams(window.location.search);
        // 이미 켜져 있으면 끈다
        if (nearMe.dataset.active === 'true') {
            ['lat', 'lon', 'radiusKm', 'page'].forEach(function (key) { params.delete(key); });
            window.location.search = params.toString();
            return;
        }
        if (!navigator.geolocation) {
            window.alert('이 브라우저에서는 위치를 알 수 없어요.');
            return;
        }
        nearMe.disabled = true;
        nearMe.classList.add('is-loading');
        navigator.geolocation.getCurrentPosition(function (position) {
            params.set('lat', position.coords.latitude.toFixed(5));
            params.set('lon', position.coords.longitude.toFixed(5));
            params.delete('page');
            window.location.search = params.toString();
        }, function () {
            nearMe.disabled = false;
            nearMe.classList.remove('is-loading');
            window.alert('위치를 가져오지 못했어요. 브라우저의 위치 권한을 확인해 주세요.');
        }, { enableHighAccuracy: false, timeout: 10000, maximumAge: 300000 });
    });
})();
