import { createApp } from 'vue'
import { createPinia } from 'pinia'
import {
  ElAlert,
  ElAside,
  ElButton,
  ElCheckbox,
  ElContainer,
  ElDatePicker,
  ElDialog,
  ElDropdown,
  ElDropdownItem,
  ElDropdownMenu,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInput,
  ElInputNumber,
  ElMain,
  ElMenu,
  ElMenuItem,
  ElOption,
  ElRadioButton,
  ElRadioGroup,
  ElScrollbar,
  ElSegmented,
  ElSelect,
  ElSpace,
  ElTable,
  ElTableColumn,
  ElTag,
  ElTooltip,
  ElLoading,
} from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import App from './App.vue'
import { initializeDesktopRuntime } from './desktop/runtime'
import { router } from './router'
import './styles/global.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)
for (const component of [
  ElAlert,
  ElAside,
  ElButton,
  ElCheckbox,
  ElContainer,
  ElDatePicker,
  ElDialog,
  ElDropdown,
  ElDropdownItem,
  ElDropdownMenu,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInput,
  ElInputNumber,
  ElMain,
  ElMenu,
  ElMenuItem,
  ElOption,
  ElRadioButton,
  ElRadioGroup,
  ElScrollbar,
  ElSegmented,
  ElSelect,
  ElSpace,
  ElTable,
  ElTableColumn,
  ElTag,
  ElTooltip,
]) {
  if (component.name) app.component(component.name, component)
}
app.use(ElLoading)

app.mount('#app')

void initializeDesktopRuntime()
