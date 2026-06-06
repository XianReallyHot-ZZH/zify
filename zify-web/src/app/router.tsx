import { Routes, Route, Navigate } from 'react-router-dom'
import MainLayout from './layouts/MainLayout'
import ChatPage from '../pages/chat/ChatPage'
import AgentListPage from '../pages/agents/AgentListPage'
import WorkflowListPage from '../pages/workflows/WorkflowListPage'
import KnowledgeListPage from '../pages/knowledge/KnowledgeListPage'
import ToolListPage from '../pages/tools/ToolListPage'
import ModelPage from '../pages/models/ModelPage'

const router = (
  <Routes>
    <Route path="/" element={<MainLayout />}>
      <Route index element={<ChatPage />} />
      <Route path="agents" element={<AgentListPage />} />
      <Route path="workflows" element={<WorkflowListPage />} />
      <Route path="knowledge" element={<KnowledgeListPage />} />
      <Route path="tools" element={<ToolListPage />} />
      <Route path="models" element={<ModelPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Route>
  </Routes>
)

export default router
