# Batalha Naval — Trabalho Prático de Redes

# O [video](https://www.youtube.com/watch?v=DSWZ7g1n4kE) está disponível no YouTube no link:

https://www.youtube.com/watch?v=DSWZ7g1n4kE

**Disciplina:** Redes de Computadores I
**Professor:** Max do Val Machado

**Alunos:**
- Antônio Drumond
- Davi Puddo
- Gabriel Valedo
- Mateus Diniz
- Raquel Motta

## Sobre o projeto

Implementação de um jogo de **Batalha Naval** multiplayer com arquitetura
cliente-servidor em Java. O servidor pareia jogadores em partidas e gerencia
a lógica do jogo; o cliente oferece uma interface gráfica (Swing) com o
tabuleiro 10x10, controle de turnos e um chat.

## Estrutura

```
src/
├── client/
│   └── Client.java      # Interface gráfica (Swing) do jogador
└── server/
    ├── Server.java      # Servidor TCP/UDP, pareamento e sessões de partida
    └── GameTable.java   # Lógica do tabuleiro, barcos e disparos
```

## Como jogar

1. No cliente, informe o host e as portas e clique em **Connect**.
2. Ao parear dois jogadores, a partida começa automaticamente.
3. No seu turno, selecione uma célula do tabuleiro e clique em **Attack**.
4. O resultado do disparo é exibido: `Hit`, `Miss`, `Destroyed` ou `GameOver`.
5. Vence quem destruir todos os barcos do adversário primeiro.
