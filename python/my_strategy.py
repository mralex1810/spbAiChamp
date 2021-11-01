from model import *


class MyStrategy:
    def get_action(self, game: Game) -> Action:
        if (game.current_tick == 0):
            return Action([MoveAction(0, game.planets.__len__() - 1, 900, None)], [], None)
        else:
            return Action([], [], None)