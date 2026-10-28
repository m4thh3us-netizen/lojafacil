-- Aparência do painel administrativo, por loja: fundo (fotografia ou cor sólida),
-- posicionamento da fotografia, escurecimento uniforme e intensidade do vidro.
-- Os limites ficam no banco para que nenhum cliente consiga salvar valores ilegíveis.
ALTER TABLE stores
    ADD COLUMN panel_bg_mode       VARCHAR(10)  NOT NULL DEFAULT 'COLOR'
        CONSTRAINT ck_panel_bg_mode CHECK (panel_bg_mode IN ('PHOTO', 'COLOR')),
    ADD COLUMN panel_bg_image_path VARCHAR(255),
    ADD COLUMN panel_bg_color      VARCHAR(7)   NOT NULL DEFAULT '#3B4656',
    ADD COLUMN panel_bg_pos_x      INT          NOT NULL DEFAULT 50
        CONSTRAINT ck_panel_bg_pos_x CHECK (panel_bg_pos_x BETWEEN 0 AND 100),
    ADD COLUMN panel_bg_pos_y      INT          NOT NULL DEFAULT 50
        CONSTRAINT ck_panel_bg_pos_y CHECK (panel_bg_pos_y BETWEEN 0 AND 100),
    -- opacidade da camada escura sobre a fotografia, em %
    ADD COLUMN panel_bg_dim        INT          NOT NULL DEFAULT 25
        CONSTRAINT ck_panel_bg_dim CHECK (panel_bg_dim BETWEEN 0 AND 70),
    -- opacidade dos painéis de vidro, em % (mais alto = mais opaco = mais legível)
    ADD COLUMN panel_glass_opacity INT          NOT NULL DEFAULT 74
        CONSTRAINT ck_panel_glass CHECK (panel_glass_opacity BETWEEN 55 AND 95);
