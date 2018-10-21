import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F
import torch.optim as optim


class PolicyValueNetwork(nn.Module):

    def __init__(self):
        
        """
        Policy-value network for Monte Carlo Tree Search
        """
        
        super().__init__()

        self.criterion_action = nn.KLDivLoss(size_average=False)
        self.criterion_value = nn.MSELoss()

    def set_optimizer(self, optimizer, **kwargs):
        """
        Set the optimizer used for training the network

        Parameters
        ----------
        optimizer : torch.optim
            optimizer

        **kwargs : optional
            parameters of the optimizer
        """
        self.optimizer = optimizer(self.parameters(), **kwargs)

    def forward(self, x):
        x = self.activation(self.input_to(x))  # hidden activation
        action = F.log_softmax(self.to_action(x), dim=1)  # output log probability
        value = self.to_value(x)
        return action, value

    def evaluate(self, X):
        Y = self.forward(torch.tensor(X).float())
        Y = [y.detach().numpy() for y in Y]  
        Y[0] = np.exp(Y[0])  # convert log probability to probability
        return Y

    def fit(self, X, Y, epochs=1, log=False):

        Xtorch = torch.from_numpy(X).float()
        Ytorch = [torch.from_numpy(y).float() for y in Y]

        log = list()
        for epoch in range(epochs):

            # Reset gradient
            self.optimizer.zero_grad()

            # Compute loss
            Zaction, Zvalue = self.forward(Xtorch)
            loss_action = self.criterion_action(Zaction, Ytorch[0]) / len(Ytorch[0])
            loss_value = self.criterion_value(Zvalue, Ytorch[1])
            loss = loss_action + loss_value
            if log:
                log.append([epoch, loss.item(), loss_action.item(), loss_value.item()])

            # Compute gradient
            loss.backward()

            # Update parameters
            self.optimizer.step()

        return log

    def save_weights(self, filename):
        torch.save(self.state_dict(), filename)

    def load_weights(self, filename):
        self.load_state_dict(torch.load(filename))

class PolicyValueMLP(PolicyValueNetwork):

    def __init__(self, input_dim, hidden_dim, action_dim, value_dim=1,
                 activation=F.tanh):

        """
        Multi layer perceptron with the following structure:

           input
             |
           hidden
             |
          --------
          |      |
        action value

        Parameters
        ----------
        input_dim : int
            number of input units
        hidden_dim : int
            number of hidden units
        action_dim : int
            number of action units
        value_dim : int, optional (default=1)
            number of value units
        activation : torch.nn.functional, optional (default=torch.nn.functional.relu)
            Non-linear activation functions
        """

        super().__init__()

        self.input_to = nn.Linear(input_dim, hidden_dim)
        self.activation = activation
        self.to_action = nn.Linear(hidden_dim, action_dim)
        self.to_value = nn.Linear(hidden_dim, value_dim)

        """
        self.input_dim = input_dim
        self.hidden_dim = hidden_dim
        self.action_dim = action_dim
        self.value_dim = value_dim
        """
        
if __name__ == "__main__":

    import numpy as np
    import os
    import argparse
    import csv

    parser = argparse.ArgumentParser(description="test run")
    parser.add_argument("optimizer", help="optimizer")
    parser.add_argument("activation", help="activation")
    parser.add_argument("n_hidden", help="number of hiddens")
    args = parser.parse_args()    
    
    def make_data(N, input_dim):
        states = [np.random.normal(0, 1, input_dim) for _ in range(N)]
        actions = [np.abs(state)/np.sum(np.abs(state)) for state in states]
        values = [np.prod(state) for state in states]
        states = np.vstack(states)
        actions = np.vstack(actions)
        values = np.vstack(values)
        return states, [actions, values]
    
    input_dim = 2
    hidden_dim = int(args.n_hidden)
    action_dim = input_dim

    Xtrain, Ytrain = make_data(10000, input_dim)
    Xtest, Ytest = make_data(10000, input_dim)

    logdir = "log_mlp_torch"
    if not os.path.exists(logdir):
        os.mkdir(logdir)
    logfile = "log_" + args.optimizer + "_" + args.activation + str(hidden_dim) + ".csv"
    logfile = os.path.join(logdir, logfile)

    if args.activation == "relu":
        activation=F.relu
    elif args.activation == "tanh":
        activation=F.tanh
    elif args.activation == "sigmoid":
        activation=F.sigmoid
    else:
        raise ValueError()
    model = PolicyValueMLP(input_dim, hidden_dim, action_dim, activation=activation)
    if args.optimizer == "adagrad":
        model.set_optimizer(optim.Adagrad)
    elif args.optimizer == "adam":
        model.set_optimizer(optim.Adam)
    elif args.optimizer == "rmsprop":
        model.set_optimizer(optim.RMSprop)
    
    log = model.fit(Xtrain, Ytrain, epochs=100)
    with open(logfile, "w") as f:
        writer = csv.writer(f)
        writer.writerows(log)
    
    Ypred = model.evaluate(Xtest)

    actions_test, values_test = Ytest
    actions_pred, values_pred = Ypred

    print(np.mean((actions_pred - actions_test)**2),
          np.mean((values_pred - values_test)**2))
    

