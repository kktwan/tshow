// 검색 화면의 작은 동작들: 지역 선택 자동 적용, "/" 로 검색창 이동, "내 주변"(위치), AI 추천 불러오기.
// 모든 선택 버튼은 링크라서 스크립트가 없어도 검색은 된다. 스크립트는 편의 기능만 더한다.
(function () {
    'use strict';

    // 지역 선택을 바꾸면 바로 적용
    document.querySelectorAll('select[data-autosubmit]').forEach(function (select) {
        select.addEventListener('change', function () {
            select.form.submit();
        });
    });

    // "/" 를 누르면 검색창으로
    var searchInput = document.querySelector('.searchbox-input');
    document.addEventListener('keydown', function (event) {
        var tag = (event.target && event.target.tagName || '').toLowerCase();
        if (event.key === '/' && searchInput && tag !== 'input' && tag !== 'textarea' && tag !== 'select') {
            event.preventDefault();
            searchInput.focus();
            searchInput.select();
        }
    });

    // 내 주변: 현재 위치를 조건에 붙인다 (이미 켜져 있으면 끈다)
    var nearMe = document.getElementById('near-me');
    if (nearMe) {
        nearMe.addEventListener('click', function () {
            var params = new URLSearchParams(window.location.search);
            if (nearMe.dataset.active === 'true') {
                ['lat', 'lon', 'radiusKm', 'page'].forEach(function (key) { params.delete(key); });
                window.location.search = params.toString();
                return;
            }
            if (!navigator.geolocation) {
                window.alert('이 브라우저에서는 위치를 알 수 없어요.');
                return;
            }
            nearMe.classList.add('is-loading');
            navigator.geolocation.getCurrentPosition(function (position) {
                // 정확한 위치가 주소창·기록에 남지 않게 소수점 셋째 자리(약 100m)까지만 쓴다
                params.set('lat', position.coords.latitude.toFixed(3));
                params.set('lon', position.coords.longitude.toFixed(3));
                params.delete('page');
                window.location.search = params.toString();
            }, function () {
                nearMe.classList.remove('is-loading');
                window.alert('위치를 가져오지 못했어요. 브라우저의 위치 권한을 확인해 주세요.');
            }, { enableHighAccuracy: false, timeout: 10000, maximumAge: 300000 });
        });
    }

    // AI 추천: 지금 보고 있는 조건으로 서버에서 추천 조각을 받아 결과 위에 끼워 넣는다
    var aiButton = document.getElementById('ai-run');
    var aiPanel = document.getElementById('ai-panel');
    if (aiButton && aiPanel) {
        var loadingHtml = '<section class="ai-panel"><div class="ai-panel-inner"><div class="ai-loading">' +
            '<p class="ai-loading-title"><svg class="i"><use href="#sparkles"/></svg>AI가 행사를 고르고 있어요…</p>' +
            '<div class="ai-skeleton"><span></span><span></span><span></span><span></span></div></div></div></section>';

        aiButton.addEventListener('click', function () {
            var params = new URLSearchParams(window.location.search);
            params.delete('page');
            aiButton.disabled = true;
            aiButton.classList.add('is-loading');
            aiPanel.innerHTML = loadingHtml;
            aiPanel.scrollIntoView({ behavior: 'smooth', block: 'nearest' });

            fetch(aiButton.dataset.endpoint + '?' + params.toString(), { headers: { 'Accept': 'text/html' } })
                .then(function (response) { return response.text(); })
                .then(function (html) { aiPanel.innerHTML = html; })
                .catch(function () {
                    aiPanel.innerHTML = '<section class="ai-panel"><div class="ai-panel-inner"><p class="ai-notice">' +
                        '<svg class="i"><use href="#info"/></svg><span>AI 추천을 불러오지 못했어요. 잠시 뒤에 다시 시도해 주세요.</span></p></div></section>';
                })
                .finally(function () {
                    aiButton.disabled = false;
                    aiButton.classList.remove('is-loading');
                });
        });

        // 패널의 닫기 버튼
        aiPanel.addEventListener('click', function (event) {
            if (event.target.closest('[data-ai-close]')) aiPanel.innerHTML = '';
        });
    }
})();
