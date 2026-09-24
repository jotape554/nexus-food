-- Fase 5: equipe do restaurante.
-- convite_pendente: usuário criado pelo administrador que ainda não definiu a própria senha.
ALTER TABLE usuarios ADD COLUMN convite_pendente BOOLEAN NOT NULL DEFAULT FALSE;
