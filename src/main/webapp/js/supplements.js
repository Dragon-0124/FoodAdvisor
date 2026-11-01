/*
 * 영양제 추천 - 기간(주/월/년) 동안 부족했던 영양소를 한 번에 채울 수 있는 종합영양제를 찾도록 돕습니다.
 *  종합영양제에 흔히 들어가는 영양소(비타민 A·C, 칼슘, 철분)는 하나의 쿠팡 검색 링크로 묶고,
 *  종합영양제로 채우기 어렵거나 보충제가 위험할 수 있는 영양소(식이섬유·칼륨·단백질)는 음식으로 안내합니다.
 *  지금은 상품 정보 없이 '쿠팡 검색 결과' 링크만 제공합니다.
 *  쿠팡 파트너스 API 키가 생기면 productsFor() 만 서버 호출로 바꾸면 상품명·가격·사진을 보여줄 수 있습니다.
 */
(() => {
  // 종합영양제로 채울 영양소: 검색어에 쓰는 이름, 주의 문구
  const IN_MULTI = {
    vitaminA: { term: '비타민A', caution: '비타민 A는 넘치면 해로울 수 있어 고함량 단일 제품보다 종합영양제가 안전해요.' },
    vitaminC: { term: '비타민C' },
    calcium:  { term: '칼슘' },
    iron:     { term: '철분', caution: '철분은 넘치면 해로울 수 있어요. 성인 남성은 철분이 든 제품이 꼭 필요한지 확인하세요.' }
  };
  // 음식으로 채우는 것이 나은 영양소
  const BY_FOOD = {
    fiber:     '잡곡밥·채소·과일·콩류를 늘려 보세요.',
    potassium: '바나나·감자·시금치·콩류로 채우는 것이 안전해요. (칼륨 보충제는 신장 질환이 있거나 약을 먹는 경우 위험할 수 있어요)',
    protein:   '살코기·생선·달걀·두부·우유를 끼니마다 곁들여 보세요.'
  };

  // 쿠팡 검색 결과 링크 (상품 데이터는 아직 없음)
  function productsFor(keyword) {
    return [{ title: '쿠팡에서 종합영양제 찾기', url: `https://www.coupang.com/np/search?q=${encodeURIComponent(keyword)}` }];
  }

  // shortages: [{ key, name, ratio(기준 대비 섭취 비율 0~1) }]
  //  → { shortages(영양제·음식으로 채울 영양소, 부족한 순), multi: { names, keyword, cautions, links } | null, foods: [{ name, tip }] }
  function plan(shortages) {
    const sorted = [...shortages].sort((a, b) => a.ratio - b.ratio);
    const multiItems = sorted.filter(item => IN_MULTI[item.key]);
    const multi = multiItems.length ? (() => {
      const keyword = ['종합비타민', ...multiItems.map(item => IN_MULTI[item.key].term)].join(' ');
      return {
        names: multiItems.map(item => item.name),
        keyword,
        cautions: multiItems.map(item => IN_MULTI[item.key].caution).filter(Boolean),
        links: productsFor(keyword)
      };
    })() : null;
    const foods = sorted.filter(item => BY_FOOD[item.key]).map(item => ({ name: item.name, tip: BY_FOOD[item.key] }));
    const relevant = sorted.filter(item => IN_MULTI[item.key] || BY_FOOD[item.key]);
    return { shortages: relevant, multi, foods };
  }

  window.FoodAdvisorSupplements = { plan };
})();
