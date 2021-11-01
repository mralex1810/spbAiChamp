import json
import random
import subprocess
import time

GENS = 12
GENERATIONS = 1000
POPULATION = 30
DEATH_PROCENT = 20
MUTATION_PROCENT = 30

out = open("out.txt", 'w')


class Chromosome(object):
    def __init__(self):
        self.gens = [0] * GENS
        for i in range(GENS):
            self.gens[i] = random.random()

    def extends(self, first, second):
        for i in range(GENS):
            if random.random() > 0.5:
                self.gens[i] = first.gens[i]
            else:
                self.gens[i] = second.gens[i]
        self.mutate()

    def mutate(self):
        for i in range(GENS):
            if random.random() < 0.2:
                self.gens[i] = self.gens[i] * (random.normalvariate(1, 0.2))

    def __str__(self):
        return str(self.gens)


def run_strategy(port, args=None):
    params = ['java', "-jar", "spb-ai-champ.jar", str(port)]
    if args:
        params.extend(map(str, args))
    return subprocess.Popen(params)


def run_game(port, seed):
    with open("config" + port + '.json', 'w') as conf:
        conf.write(json.dumps(
            {
                "seed": seed,
                "game": {"Create": "QualA"},
                "players": [
                    {
                        "Tcp": {
                            "host": None,
                            "port": int(port),
                            "accept_timeout": None,
                            "single_timeout": 1,
                            "total_time_limit": None,
                            "token": None
                        }
                    },
                    {
                        "Empty": None
                    }
                ]
            }
        ))
    params = ['./spbaichamp', "--batch-mode",
              '--config=config' + port + '.json',
              '--save-results=results' + port + '.json']
    ans = subprocess.Popen(params)
    return ans


def run(port, seed, gens):
    run_game(port, seed)
    time.sleep(1)
    run_strategy(port, gens)


def parse_result(port):
    with open("results" + port + ".json") as js:
        d = json.loads(js.read())
        res = d["results"][0]
        print(port, ": ", res)
        return res


def main():
    random.seed(time.time())
    common_seed = 9825335018550721964
    strategies = []
    for i in range(POPULATION):
        strategies.append(Chromosome())
    for generation in range(GENERATIONS):
        seed = random.randint(1, 999999999999999999)
        for strategy in range(POPULATION):
            if strategy < 10:
                run("3100" + str(strategy), seed, strategies[strategy].gens)
            else:
                run("310" + str(strategy), seed, strategies[strategy].gens)
        time.sleep(5)
        results = [0] * POPULATION
        for port in range(POPULATION):
            if port < 10:
                results[port] = [parse_result("3100" + str(port)), port]
            else:
                results[port] = [parse_result("310" + str(port)), port]
        print("GENERATION: ", generation)
        results.sort()
        run("31100", common_seed, strategies[results[-1][1]].gens)
        time.sleep(3)
        res = parse_result("31100")
        out.write(str(res))
        out.write(" {")
        for gen in strategies[results[-1][1]].gens:
            out.write(str(gen))
            out.write("f, ")
        out.write("} ")
        out.write(str(results))
        out.write(" [")
        for c in strategies:
            out.write(str(c))
            out.write(", ")
        out.write(']\n')
        out.flush()
        for i in range(POPULATION * DEATH_PROCENT // 100):
            strategies[results[i][1]] = Chromosome()
            strategies[results[i][1]].extends(strategies[results[random.randint(i + 1, POPULATION - 1)][1]],
                                              strategies[results[random.randint(0, POPULATION - 1)][1]])
        for i in range(POPULATION):
            if random.random() < MUTATION_PROCENT:
                strategies[i].mutate()


if __name__ == "__main__":
    main()
