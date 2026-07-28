<script setup>
/**
 * 혜택 허브 (2026-07-28) — 적립금·등급·쿠폰을 한 곳에. 이전엔 적립금·등급이 /settings 에 계정설정과
 * 섞여 있었고 쿠폰은 볼 데가 없었다. 혜택("쓸 수 있는 것")과 계정설정은 성격이 달라 자리를 나눈다
 * (마켓컬리·쿠팡식). 계정설정(닉네임·비번·주소·알림·탈퇴)은 /settings 로.
 */
import PointPanel from '../components/PointPanel.vue';
import CouponWallet from '../components/CouponWallet.vue';
</script>

<template>
  <section class="page-narrow">
    <h1 class="page-title mb-5">혜택</h1>

    <!-- 적립금 · 등급 -->
    <PointPanel />

    <!-- 쿠폰함 -->
    <div class="mt-8">
      <CouponWallet />
    </div>
  </section>
</template>
