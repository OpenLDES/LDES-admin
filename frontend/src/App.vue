<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute } from 'vue-router'
import Toast from 'primevue/toast'
import ConfirmDialog from 'primevue/confirmdialog'
import AppSidebar from '@/components/layout/AppSidebar.vue'
import AppTopbar from '@/components/layout/AppTopbar.vue'
import { useInstancesStore } from '@/stores/instances'
import { useUiStore } from '@/stores/ui'

const route = useRoute()
const instances = useInstancesStore()
useUiStore()

onMounted(() => instances.ensureLoaded())
</script>

<template>
  <div class="layout">
    <AppSidebar />
    <div class="layout-main">
      <AppTopbar />
      <main class="layout-content" :class="{ 'full-bleed': route.meta.fullBleed }">
        <RouterView />
      </main>
    </div>
    <Toast position="bottom-right" />
    <ConfirmDialog />
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  min-height: 100vh;
}

.layout-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.layout-content {
  flex: 1;
  min-height: 0;
}

.layout-content.full-bleed {
  /* A fixed height instead of growing with the content: the view scrolls inside */
  flex: none;
  height: calc(100vh - var(--topbar-height));
  overflow: hidden;
}
</style>
