import argparse
import os
from os import system

from config import *


def main():
    parser = argparse.ArgumentParser(prog=os.path.basename(__file__))
    globals().update(load_config(parser))
    parser.add_argument('--dataset', choices=datasets, required=False)
    parser.add_argument('--strategy', choices=strategies, required=False)
    parser.add_argument('--generation', choices=generations, required=False)
    parser.add_argument('--seed', choices=seeds, required=False)

    args = parser.parse_args()

    # override default values
    selected_datasets = [args.dataset] if args.dataset else datasets
    selected_strategies = [args.strategy] if args.strategy else strategies
    selected_generations = [args.generation] if args.generation else generations
    selected_seeds = [args.seed] if args.seed else seeds

    for dataset in selected_datasets:
        for strategy in selected_strategies:
            if strategy not in ['DEFAULT', 'RAND']:
                for generation in selected_generations:
                    d = {"dataset": dataset, "strategy": strategy, "generation": generation}
                    experiment = "{dataset}.{strategy}.{generation}-{dataset}".format(**d)
                    folder = '%s/%s/%s' % (os.environ['AUTOWEKA_PATH'], experiments_folder, experiment)
                    for seed in selected_seeds:
                        filename = 'training.predictions.%s.csv' % seed
                        # avoid computing existing predictions
                        if not os.path.isfile(os.path.join(folder, filename)):
                            command = 'qsub -N %s -l q=compute ./launch_training_prediction.sh %s %s %s %s' % (
                                experiment, experiments_folder, experiment, dataset, seed)
                            print command
                            system(command)


if __name__ == "__main__":
    main()
