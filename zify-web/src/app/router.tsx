import { Routes, Route, Navigate } from 'react-router-dom'
import MainLayout from './layouts/MainLayout'
import HomePage from '../pages/home/HomePage'
import ChatPage from '../pages/chat/ChatPage'
import AgentListPage from '../pages/agents/AgentListPage'
import AgentFormPage from '../pages/agents/AgentFormPage'
import WorkflowListPage from '../pages/workflows/WorkflowListPage'
import WorkflowEditorPage from '../pages/workflows/WorkflowEditorPage'
import KnowledgeListPage from '../pages/knowledge/KnowledgeListPage'
import KnowledgeDetailPage from '../pages/knowledge/KnowledgeDetailPage'
import ToolListPage from '../pages/tools/ToolListPage'
import ToolFormPage from '../pages/tools/ToolFormPage'
import ModelPage from '../pages/models/ModelPage'

const router = (
  <Routes>
    <Route path="/" element={<MainLayout />}>
      <Route index element={<HomePage />} />
      <Route path="chat" element={<ChatPage />} />
      <Route path="agents" element={<AgentListPage />} />
      <Route path="agents/create" element={<AgentFormPage />} />
      <Route path="agents/:id/edit" element={<AgentFormPage />} />
      <Route path="workflows" element={<WorkflowListPage />} />
      <Route path="workflows/:id" element={<WorkflowEditorPage />} />
      <Route path="knowledge" element={<KnowledgeListPage />} />
      <Route path="knowledge/:id" element={<KnowledgeDetailPage />} />
      <Route path="tools" element={<ToolListPage />} />
      <Route path="tools/create" element={<ToolFormPage />} />
      <Route path="tools/:id/edit" element={<ToolFormPage />} />
      <Route path="models" element={<ModelPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Route>
  </Routes>
)

export default router
